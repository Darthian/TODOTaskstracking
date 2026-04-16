package com.todotracker.service

import com.todotracker.dto.CreateTaskRequest
import com.todotracker.model.Task
import com.todotracker.model.TaskStatus
import com.todotracker.model.TimeEntry
import com.todotracker.repository.TaskRepository
import com.todotracker.repository.TimeEntryRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Integration tests for [TaskService] against a real H2 in-memory database.
 *
 * The `dev` Spring profile is activated by application.yml, providing the H2 datasource.
 * Each test runs in its own transaction that is rolled back after the test completes,
 * so H2 state is clean between tests without requiring schema recreation.
 *
 * These tests verify end-to-end timer lifecycle including pause/resume patterns,
 * midnight boundary conditions, and realistic multi-session user workflows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TaskServiceIntegrationTest {

    @Autowired
    lateinit var service: TaskService

    @Autowired
    lateinit var taskRepository: TaskRepository

    @Autowired
    lateinit var timeEntryRepository: TimeEntryRepository

    // =========================================================================
    // IT-1  Realistic user session — create → start → stop → resume → stop
    // =========================================================================

    @Test
    fun `realistic session create-start-stop-resume-stop accumulates time correctly`() {
        // 1. Create task
        val created = service.createTask(CreateTaskRequest("Implement feature X"))
        assertEquals(0L, created.completedSeconds)
        assertFalse(created.isTimerRunning)

        val id = created.id

        // 2. First session: start
        val afterStart = service.startTimer(id)
        assertTrue(afterStart.isTimerRunning)
        assertNotNull(afterStart.timerStartedAt)
        assertEquals(0L, afterStart.completedSeconds)

        // 3. First session: stop  (simulates pause)
        val afterStop1 = service.stopTimer(id)
        assertFalse(afterStop1.isTimerRunning)
        assertNull(afterStop1.timerStartedAt)
        assertTrue(afterStop1.completedSeconds >= 0L)
        assertEquals(afterStop1.completedSeconds, afterStop1.totalSeconds)

        val bankAfterSession1 = afterStop1.completedSeconds

        // 4. Second session: start  (simulates resume)
        val afterResume = service.startTimer(id)
        assertTrue(afterResume.isTimerRunning)
        // Banked time from session 1 must be preserved
        assertEquals(bankAfterSession1, afterResume.completedSeconds)
        assertTrue(afterResume.totalSeconds >= bankAfterSession1)

        // 5. Second session: stop
        val afterStop2 = service.stopTimer(id)
        assertFalse(afterStop2.isTimerRunning)
        assertTrue(afterStop2.completedSeconds >= bankAfterSession1,
            "Accumulated time must be >= session-1 bank; got ${afterStop2.completedSeconds}")
        assertEquals(afterStop2.completedSeconds, afterStop2.totalSeconds)
    }

    // =========================================================================
    // IT-2  Start guard — double-start must return 409-equivalent
    // =========================================================================

    @Test
    fun `double start throws IllegalStateException without creating a second open entry`() {
        val created = service.createTask(CreateTaskRequest("Double-start guard"))
        service.startTimer(created.id)

        assertThrows<IllegalStateException> { service.startTimer(created.id) }

        // Database must still have exactly one open entry
        val openEntries = timeEntryRepository.findAllByTaskId(created.id).filter { it.endTime == null }
        assertEquals(1, openEntries.size, "Exactly one open TimeEntry must exist after a failed double-start")
    }

    // =========================================================================
    // IT-3  Stop guard — double-stop must return 409-equivalent
    // =========================================================================

    @Test
    fun `double stop throws IllegalStateException without modifying existing entries`() {
        val created = service.createTask(CreateTaskRequest("Double-stop guard"))
        service.startTimer(created.id)
        service.stopTimer(created.id)

        assertThrows<IllegalStateException> { service.stopTimer(created.id) }

        // The single closed entry must remain unchanged
        val entries = timeEntryRepository.findAllByTaskId(created.id)
        assertEquals(1, entries.size)
        assertNotNull(entries[0].endTime)
    }

    // =========================================================================
    // IT-4  Delete safety — running timer must be persisted before deletion
    // =========================================================================

    @Test
    fun `delete task with running timer closes the entry before removing it`() {
        val created = service.createTask(CreateTaskRequest("Task to delete"))
        val id = created.id
        service.startTimer(id)
        assertTrue(service.getTaskById(id).isTimerRunning)

        // deleteTask must not throw; it must stop the timer first
        service.deleteTask(id)

        // Task is gone
        assertThrows<NoSuchElementException> { service.getTaskById(id) }

        // No orphaned open entries in the database (entries were deleted, but if the
        // repository still existed we'd expect null — verify via direct repo query)
        assertNull(timeEntryRepository.findByTaskIdAndEndTimeIsNull(id),
            "No open TimeEntry must survive after task deletion")
    }

    // =========================================================================
    // IT-5  Midnight boundary — TimeEntry spanning midnight accumulates correctly
    // =========================================================================

    @Test
    fun `accumulation is correct for a finished entry that spans midnight`() {
        val created = service.createTask(CreateTaskRequest("Night-shift work"))
        val jpaTask = taskRepository.findById(created.id).get()

        // Simulate 20 minutes of work straddling midnight: 23:50 → 00:10
        val startBeforeMidnight = LocalDateTime.of(2024, 3, 14, 23, 50, 0)
        val endAfterMidnight    = LocalDateTime.of(2024, 3, 15,  0, 10, 0)
        val expectedDuration    = ChronoUnit.SECONDS.between(startBeforeMidnight, endAfterMidnight) // 1200

        timeEntryRepository.save(
            TimeEntry(
                task = jpaTask,
                startTime = startBeforeMidnight,
                endTime = endAfterMidnight,
                durationSeconds = expectedDuration
            )
        )

        val response = service.getTaskById(created.id)

        assertEquals(1200L, response.completedSeconds,
            "20 min spanning midnight must accumulate to 1200 s")
        assertEquals(1200L, response.totalSeconds)
        assertFalse(response.isTimerRunning)
        assertNull(response.timerStartedAt)
    }

    // =========================================================================
    // IT-6  Multiple pause/resume cycles — totals across injected past sessions
    // =========================================================================

    @Test
    fun `multiple injected past sessions and a live session accumulate correctly`() {
        val created = service.createTask(CreateTaskRequest("Long running project"))
        val id = created.id
        val jpaTask = taskRepository.findById(id).get()

        val base = LocalDateTime.now().minusHours(5)

        // Inject 3 completed sessions totalling 1800 s
        timeEntryRepository.save(TimeEntry(task = jpaTask, startTime = base,
            endTime = base.plusSeconds(600), durationSeconds = 600))
        timeEntryRepository.save(TimeEntry(task = jpaTask, startTime = base.plusHours(1),
            endTime = base.plusHours(1).plusSeconds(900), durationSeconds = 900))
        timeEntryRepository.save(TimeEntry(task = jpaTask, startTime = base.plusHours(2),
            endTime = base.plusHours(2).plusSeconds(300), durationSeconds = 300))

        // Verify banked total before starting a new session
        val banked = service.getTaskById(id)
        assertEquals(1800L, banked.completedSeconds)
        assertFalse(banked.isTimerRunning)

        // Start a fourth session (simulates resume after multiple pauses)
        service.startTimer(id)
        val live = service.getTaskById(id)

        assertEquals(1800L, live.completedSeconds,
            "Banked seconds must not change when a new session starts")
        assertTrue(live.totalSeconds >= 1800L)
        assertTrue(live.isTimerRunning)
        assertNotNull(live.timerStartedAt)

        // Stop and confirm grand total
        val final = service.stopTimer(id)
        assertTrue(final.completedSeconds >= 1800L,
            "Final completedSeconds must be ≥ 1800 s; got ${final.completedSeconds}")
        assertFalse(final.isTimerRunning)
    }

    // =========================================================================
    // IT-7  Weekly summary — realistic multi-task session
    // =========================================================================

    @Test
    fun `getWeeklySummary groups tasks correctly and sorts by totalSeconds descending`() {
        val weekStart = LocalDateTime.now().minusDays(3)
            .withHour(0).withMinute(0).withSecond(0).withNano(0)

        val alpha = service.createTask(CreateTaskRequest("Alpha task"))
        val beta  = service.createTask(CreateTaskRequest("Beta task"))
        val jpaAlpha = taskRepository.findById(alpha.id).get()
        val jpaBeta  = taskRepository.findById(beta.id).get()

        val base = weekStart.plusHours(2)

        // Alpha: two sessions = 2400 + 1200 = 3600 s
        timeEntryRepository.save(TimeEntry(task = jpaAlpha, startTime = base,
            endTime = base.plusSeconds(2400), durationSeconds = 2400))
        timeEntryRepository.save(TimeEntry(task = jpaAlpha, startTime = base.plusHours(3),
            endTime = base.plusHours(3).plusSeconds(1200), durationSeconds = 1200))

        // Beta: one session = 1800 s
        timeEntryRepository.save(TimeEntry(task = jpaBeta, startTime = base.plusHours(6),
            endTime = base.plusHours(6).plusSeconds(1800), durationSeconds = 1800))

        val summary = service.getWeeklySummary(weekStart)

        val alphaEntry = summary.entries.find { it.taskId == alpha.id }!!
        val betaEntry  = summary.entries.find { it.taskId == beta.id }!!

        assertEquals(3600L, alphaEntry.totalSeconds)
        assertEquals(1800L, betaEntry.totalSeconds)
        assertEquals(5400L, summary.grandTotalSeconds)

        // Must be sorted by totalSeconds descending
        assertEquals(alpha.id, summary.entries.first().taskId,
            "Alpha (3600 s) must rank before Beta (1800 s)")
    }
}
