package com.todotracker.service

import com.todotracker.dto.CreateTaskRequest
import com.todotracker.model.Task
import com.todotracker.model.TaskStatus
import com.todotracker.model.TimeEntry
import com.todotracker.repository.TaskRepository
import com.todotracker.repository.TimeEntryRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.LocalDateTime
import java.util.Optional

/**
 * Unit tests for [TaskService] timer logic.
 *
 * Both repositories are mocked — no Spring context, no database.
 * Clock-sensitive boundary conditions (midnight, clock skew) are handled by
 * injecting fixed startTime values into mocked [TimeEntry] objects and
 * asserting relationships (>= , == 0) rather than exact values.
 */
class TaskServiceTest {

    private lateinit var taskRepository: TaskRepository
    private lateinit var timeEntryRepository: TimeEntryRepository
    private lateinit var service: TaskService

    private val TASK_ID = 1L
    private val task = Task(id = TASK_ID, title = "Write unit tests", status = TaskStatus.IN_PROGRESS)

    @BeforeEach
    fun setUp() {
        taskRepository = mock()
        timeEntryRepository = mock()
        service = TaskService(taskRepository, timeEntryRepository)

        // Default: task exists; no running or banked entries
        whenever(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task))
        whenever(taskRepository.save(any<Task>())).thenAnswer { it.arguments[0] as Task }
        whenever(timeEntryRepository.findByTaskIdAndEndTimeIsNull(TASK_ID)).thenReturn(null)
        whenever(timeEntryRepository.findAllByTaskId(TASK_ID)).thenReturn(emptyList())
        whenever(timeEntryRepository.save(any<TimeEntry>())).thenAnswer { it.arguments[0] as TimeEntry }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun finishedEntry(start: LocalDateTime, durationSeconds: Long): TimeEntry =
        TimeEntry(
            task = task,
            startTime = start,
            endTime = start.plusSeconds(durationSeconds),
            durationSeconds = durationSeconds
        )

    private fun openEntry(start: LocalDateTime): TimeEntry = TimeEntry(task = task, startTime = start)

    // =========================================================================
    // startTimer
    // =========================================================================

    @Test
    fun `startTimer creates a new open TimeEntry and returns isTimerRunning true`() {
        val now = LocalDateTime.now()
        // After save, findAllByTaskId returns the new open entry
        whenever(timeEntryRepository.findAllByTaskId(TASK_ID)).thenReturn(listOf(openEntry(now)))

        val response = service.startTimer(TASK_ID)

        verify(timeEntryRepository).save(any<TimeEntry>())
        assertTrue(response.isTimerRunning)
        assertNotNull(response.timerStartedAt)
        assertEquals(0L, response.completedSeconds)
    }

    @Test
    fun `startTimer throws IllegalStateException and does not save when timer already running`() {
        whenever(timeEntryRepository.findByTaskIdAndEndTimeIsNull(TASK_ID))
            .thenReturn(openEntry(LocalDateTime.now().minusMinutes(5)))

        assertThrows<IllegalStateException> { service.startTimer(TASK_ID) }

        verify(timeEntryRepository, never()).save(any())
    }

    // =========================================================================
    // stopTimer
    // =========================================================================

    @Test
    fun `stopTimer closes the open entry and persists non-negative durationSeconds`() {
        val startTime = LocalDateTime.now().minusSeconds(90)
        val running = openEntry(startTime)
        whenever(timeEntryRepository.findByTaskIdAndEndTimeIsNull(TASK_ID)).thenReturn(running)
        // After mutating `running`, findAllByTaskId returns it (now closed)
        whenever(timeEntryRepository.findAllByTaskId(TASK_ID)).thenAnswer { listOf(running) }

        service.stopTimer(TASK_ID)

        assertNotNull(running.endTime)
        assertTrue(running.durationSeconds >= 90L, "Expected ≥ 90 s elapsed but got ${running.durationSeconds}")
        assertTrue(running.durationSeconds >= 0L, "durationSeconds must never be negative")
        verify(timeEntryRepository).save(running)
    }

    @Test
    fun `stopTimer throws IllegalStateException when no timer is running`() {
        // Default mock: findByTaskIdAndEndTimeIsNull returns null
        assertThrows<IllegalStateException> { service.stopTimer(TASK_ID) }
        verify(timeEntryRepository, never()).save(any())
    }

    // =========================================================================
    // Clock skew guard — coerceAtLeast(0)
    // =========================================================================

    @Test
    fun `stopTimer coerces negative duration to zero when startTime is in the future`() {
        // Simulate a clock-skewed entry whose startTime is 5 s ahead of the server clock
        val futureStart = LocalDateTime.now().plusSeconds(5)
        val running = openEntry(futureStart)
        whenever(timeEntryRepository.findByTaskIdAndEndTimeIsNull(TASK_ID)).thenReturn(running)
        whenever(timeEntryRepository.findAllByTaskId(TASK_ID)).thenAnswer { listOf(running) }

        service.stopTimer(TASK_ID)

        assertEquals(0L, running.durationSeconds,
            "Negative duration from clock skew must be coerced to 0")
    }

    // =========================================================================
    // buildResponse — accumulation across multiple entries
    // =========================================================================

    @Test
    fun `getTaskById sums durationSeconds across all finished entries`() {
        val base = LocalDateTime.now().minusHours(3)
        whenever(timeEntryRepository.findAllByTaskId(TASK_ID)).thenReturn(
            listOf(
                finishedEntry(base, 600),
                finishedEntry(base.plusHours(1), 900),
                finishedEntry(base.plusHours(2), 300)
            )
        )

        val response = service.getTaskById(TASK_ID)

        assertEquals(1800L, response.completedSeconds)
        assertEquals(1800L, response.totalSeconds)
        assertFalse(response.isTimerRunning)
        assertNull(response.timerStartedAt)
    }

    @Test
    fun `getTaskById adds live seconds of running entry on top of completed`() {
        val base = LocalDateTime.now().minusHours(2)
        val openStart = LocalDateTime.now().minusSeconds(120)
        whenever(timeEntryRepository.findAllByTaskId(TASK_ID)).thenReturn(
            listOf(
                finishedEntry(base, 600),
                openEntry(openStart)
            )
        )

        val response = service.getTaskById(TASK_ID)

        assertEquals(600L, response.completedSeconds)
        assertTrue(response.totalSeconds >= 720L,
            "totalSeconds must be completedSeconds + ≥ 120 s live, got ${response.totalSeconds}")
        assertTrue(response.isTimerRunning)
        assertEquals(openStart, response.timerStartedAt)
    }

    @Test
    fun `getTaskById returns all zeros for a brand-new task with no entries`() {
        val response = service.getTaskById(TASK_ID)

        assertEquals(0L, response.completedSeconds)
        assertEquals(0L, response.totalSeconds)
        assertFalse(response.isTimerRunning)
        assertNull(response.timerStartedAt)
    }

    // =========================================================================
    // Midnight / cross-day boundary — getTaskById
    // =========================================================================

    @Test
    fun `getTaskById counts live seconds correctly for timer started before midnight`() {
        // Timer started 10 min before yesterday midnight — now is today, so > 10 min elapsed
        val startBeforeMidnight = LocalDateTime.now().minusDays(1)
            .toLocalDate().atTime(23, 50, 0)
        whenever(timeEntryRepository.findAllByTaskId(TASK_ID)).thenReturn(
            listOf(openEntry(startBeforeMidnight))
        )

        val response = service.getTaskById(TASK_ID)

        assertTrue(response.totalSeconds >= 600L,
            "Expected ≥ 600 s (10 min from before midnight to now) but got ${response.totalSeconds}")
        assertTrue(response.totalSeconds >= 0L, "totalSeconds must never be negative")
        assertTrue(response.isTimerRunning)
    }

    // =========================================================================
    // deleteTask — running-timer safety
    // =========================================================================

    @Test
    fun `deleteTask stops and persists running timer before deleting entries and task`() {
        val running = openEntry(LocalDateTime.now().minusMinutes(10))
        whenever(timeEntryRepository.findByTaskIdAndEndTimeIsNull(TASK_ID)).thenReturn(running)

        service.deleteTask(TASK_ID)

        // Running entry must be closed before any deletion happens
        assertNotNull(running.endTime, "Running entry endTime must be set before deletion")
        assertTrue(running.durationSeconds >= 0L)

        val order = inOrder(timeEntryRepository, taskRepository)
        order.verify(timeEntryRepository).save(running)           // entry closed first
        order.verify(timeEntryRepository).deleteAllByTaskId(TASK_ID)
        order.verify(taskRepository).deleteById(TASK_ID)
    }

    @Test
    fun `deleteTask with no running timer skips save and deletes directly`() {
        // Default mock: no running entry

        service.deleteTask(TASK_ID)

        verify(timeEntryRepository, never()).save(any())
        verify(timeEntryRepository).deleteAllByTaskId(TASK_ID)
        verify(taskRepository).deleteById(TASK_ID)
    }

    // =========================================================================
    // getWeeklySummary — running-timer handling and week-boundary clamping
    // =========================================================================

    @Test
    fun `getWeeklySummary counts running entry up to now when within the week`() {
        val weekStart = LocalDateTime.now().minusDays(2).withHour(0).withMinute(0).withSecond(0).withNano(0)
        val task2 = Task(id = 2L, title = "Research")
        val openStart = LocalDateTime.now().minusMinutes(30)

        whenever(timeEntryRepository.findEntriesInRange(any(), any())).thenReturn(
            listOf(
                TimeEntry(task = task,  startTime = weekStart.plusHours(1),
                          endTime = weekStart.plusHours(2), durationSeconds = 3600),
                TimeEntry(task = task2, startTime = openStart, endTime = null, durationSeconds = 0)
            )
        )

        val summary = service.getWeeklySummary(weekStart)

        val t1 = summary.entries.find { it.taskId == TASK_ID }!!
        val t2 = summary.entries.find { it.taskId == 2L }!!

        assertEquals(3600L, t1.totalSeconds)
        assertTrue(t2.totalSeconds >= 1800L, "Live timer for task2 must be ≥ 30 min")
        assertTrue(summary.grandTotalSeconds >= 3600L + 1800L)
    }

    @Test
    fun `getWeeklySummary clamps live timer to week boundary when week is in the past`() {
        // weekEnd is yesterday → now > weekEnd → effectiveEnd = weekEnd
        val weekStart = LocalDateTime.now().minusDays(8).withHour(0).withMinute(0).withSecond(0).withNano(0)
        val weekEnd = weekStart.plusWeeks(1)              // ≈ yesterday
        val openStart = weekEnd.minusHours(1)             // 1 h before week end

        whenever(timeEntryRepository.findEntriesInRange(any(), any())).thenReturn(
            listOf(TimeEntry(task = task, startTime = openStart, endTime = null, durationSeconds = 0))
        )

        val summary = service.getWeeklySummary(weekStart)

        assertEquals(3600L, summary.entries.first().totalSeconds,
            "Live seconds must be clamped to week boundary (3600 s)")
    }
}
