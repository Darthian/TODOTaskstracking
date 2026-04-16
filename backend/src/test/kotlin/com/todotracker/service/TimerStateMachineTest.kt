package com.todotracker.service

import com.todotracker.model.Task
import com.todotracker.model.TaskStatus
import com.todotracker.model.TimeEntry
import com.todotracker.repository.TimeEntryRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.LocalDateTime

/**
 * Unit tests for [TimerStateMachine] and [TimerAccumulator].
 *
 * No Spring context — TimeEntryRepository is mocked with Mockito-Kotlin.
 * All timestamps are deterministic via the injected `clock` parameter.
 *
 * Task ID 4 is used throughout (matches the "task4" invocation context).
 */
class TimerStateMachineTest {

    // ─── Shared fixtures ──────────────────────────────────────────────────────

    private val TASK_ID = 4L
    private val BASE = LocalDateTime.of(2024, 1, 15, 10, 0, 0)
    private val task = Task(id = TASK_ID, title = "Sample Task", status = TaskStatus.IN_PROGRESS)

    // ─── Fixture helpers ──────────────────────────────────────────────────────

    /** A closed entry that ran for [durationSeconds] seconds starting at [start]. */
    private fun finishedEntry(start: LocalDateTime, durationSeconds: Long): TimeEntry =
        TimeEntry(
            task = task,
            startTime = start,
            endTime = start.plusSeconds(durationSeconds),
            durationSeconds = durationSeconds
        )

    /** An open (running) entry starting at [start]. */
    private fun openEntry(start: LocalDateTime): TimeEntry =
        TimeEntry(task = task, startTime = start)

    /**
     * Builds a mock [TimeEntryRepository] pre-configured with:
     *  - [openEntry] returned by [TimeEntryRepository.findByTaskIdAndEndTimeIsNull]
     *  - [allEntries] returned by [TimeEntryRepository.findAllByTaskId]
     *  - `save` echoes back whatever is passed to it
     */
    private fun mockRepo(
        openEntry: TimeEntry? = null,
        allEntries: List<TimeEntry> = emptyList()
    ): TimeEntryRepository {
        val repo = mock<TimeEntryRepository>()
        whenever(repo.findByTaskIdAndEndTimeIsNull(TASK_ID)).thenReturn(openEntry)
        whenever(repo.findAllByTaskId(TASK_ID)).thenReturn(allEntries)
        whenever(repo.save(any<TimeEntry>())).thenAnswer { it.arguments[0] as TimeEntry }
        return repo
    }

    // =========================================================================
    // State machine tests — TimerStateMachine
    // =========================================================================

    // ── Test 1 ────────────────────────────────────────────────────────────────
    @Test
    fun `start from IDLE creates new open entry and transitions to RUNNING`() {
        // IDLE: no open entry, no prior entries
        val repo = mockRepo(openEntry = null, allEntries = emptyList())
        val sm = TimerStateMachine(repo)

        val entry = sm.start(TASK_ID, task, BASE)

        verify(repo, times(1)).save(any())
        assertNull(entry.endTime, "New entry must have endTime == null (running)")
        assertEquals(BASE, entry.startTime)
        // Verify state is now derivable as RUNNING
        whenever(repo.findByTaskIdAndEndTimeIsNull(TASK_ID)).thenReturn(entry)
        assertEquals(TimerState.RUNNING, sm.currentState(TASK_ID))
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────
    @Test
    fun `stop from RUNNING closes entry with correct durationSeconds`() {
        val open = openEntry(BASE)
        val stopClock = BASE.plusSeconds(300)
        val repo = mockRepo(openEntry = open, allEntries = listOf(open))
        val sm = TimerStateMachine(repo)

        val closed = sm.stop(TASK_ID, stopClock)

        assertNotNull(closed)
        assertEquals(stopClock, closed!!.endTime)
        assertEquals(300L, closed.durationSeconds)
        verify(repo, times(1)).save(any())
    }

    // ── Test 3 ────────────────────────────────────────────────────────────────
    @Test
    fun `pause from RUNNING closes entry identically to stop`() {
        val open = openEntry(BASE)
        val pauseClock = BASE.plusSeconds(120)
        val repo = mockRepo(openEntry = open, allEntries = listOf(open))
        val sm = TimerStateMachine(repo)

        val closed = sm.pause(TASK_ID, pauseClock)

        assertEquals(pauseClock, closed.endTime)
        assertEquals(120L, closed.durationSeconds)
        verify(repo, times(1)).save(any())
    }

    // ── Test 4 ────────────────────────────────────────────────────────────────
    @Test
    fun `resume from PAUSED creates new open entry`() {
        val banked = finishedEntry(BASE, 60)
        val resumeClock = BASE.plusSeconds(180)
        // PAUSED: no open entry, but prior closed entries exist
        val repo = mockRepo(openEntry = null, allEntries = listOf(banked))
        val sm = TimerStateMachine(repo)

        val entry = sm.resume(TASK_ID, task, resumeClock)

        verify(repo, times(1)).save(any())
        assertNull(entry.endTime, "Resumed entry must be open (endTime == null)")
        assertEquals(resumeClock, entry.startTime)
    }

    // ── Test 5 ────────────────────────────────────────────────────────────────
    @Test
    fun `stop from PAUSED is a no-op and does not call save`() {
        val banked = finishedEntry(BASE, 60)
        // PAUSED: no open entry
        val repo = mockRepo(openEntry = null, allEntries = listOf(banked))
        val sm = TimerStateMachine(repo)

        // stop() returns null when nothing is running (PAUSED or IDLE)
        val result = sm.stop(TASK_ID, BASE.plusSeconds(300))

        assertNull(result, "stop() from PAUSED must return null (no-op)")
        verify(repo, never()).save(any())
    }

    // ── Test 6 ────────────────────────────────────────────────────────────────
    @Test
    fun `start when already RUNNING throws IllegalStateException`() {
        val open = openEntry(BASE)
        val repo = mockRepo(openEntry = open, allEntries = listOf(open))
        val sm = TimerStateMachine(repo)

        assertThrows<IllegalStateException> {
            sm.start(TASK_ID, task, BASE.plusSeconds(60))
        }
    }

    // ── Test 7 ────────────────────────────────────────────────────────────────
    @Test
    fun `resume when already RUNNING throws IllegalStateException`() {
        val open = openEntry(BASE)
        val repo = mockRepo(openEntry = open, allEntries = listOf(open))
        val sm = TimerStateMachine(repo)

        assertThrows<IllegalStateException> {
            sm.resume(TASK_ID, task, BASE.plusSeconds(60))
        }
    }

    // ── Test 8 ────────────────────────────────────────────────────────────────
    @Test
    fun `pause when IDLE throws IllegalStateException`() {
        // IDLE: no entries at all
        val repo = mockRepo(openEntry = null, allEntries = emptyList())
        val sm = TimerStateMachine(repo)

        assertThrows<IllegalStateException> {
            sm.pause(TASK_ID, BASE)
        }
    }

    // =========================================================================
    // Accumulator tests — TimerAccumulator
    // =========================================================================

    // ── Test 9 ────────────────────────────────────────────────────────────────
    @Test
    fun `normal cycle with two finished entries accumulates completedSeconds only`() {
        val entries = listOf(
            finishedEntry(BASE, 30),
            finishedEntry(BASE.plusMinutes(10), 45)
        )

        val result = TimerAccumulator.accumulate(entries, BASE.plusMinutes(30))

        assertEquals(75L, result.completedSeconds)
        assertEquals(0L, result.liveSeconds)
        assertEquals(75L, result.totalSeconds)
        assertFalse(result.isRunning)
        assertNull(result.timerStartedAt)
    }

    // ── Test 10 ───────────────────────────────────────────────────────────────
    @Test
    fun `live timer adds elapsed seconds on top of completed entries`() {
        val openStart = BASE.plusMinutes(5)
        val now = openStart.plusSeconds(90)
        val entries = listOf(
            finishedEntry(BASE, 60),
            openEntry(openStart)
        )

        val result = TimerAccumulator.accumulate(entries, now)

        assertEquals(60L, result.completedSeconds)
        assertEquals(90L, result.liveSeconds)
        assertEquals(150L, result.totalSeconds)
        assertTrue(result.isRunning)
        assertEquals(openStart, result.timerStartedAt)
    }

    // ── Test 11 ───────────────────────────────────────────────────────────────
    @Test
    fun `pause at midnight correctly spans day boundary without negative values`() {
        // Timer started 30 s before midnight Jan 14; now is 30 s after midnight Jan 15
        val midnightStart = LocalDateTime.of(2024, 1, 14, 23, 59, 30)
        val now           = LocalDateTime.of(2024, 1, 15,  0,  0, 30)
        val entries = listOf(openEntry(midnightStart))

        val result = TimerAccumulator.accumulate(entries, now)

        assertEquals(60L, result.liveSeconds, "Must count 60 s across the midnight boundary")
        assertTrue(result.liveSeconds >= 0L, "liveSeconds must never be negative")
        assertEquals(midnightStart, result.timerStartedAt)
        assertTrue(result.isRunning)
        assertEquals(0L, result.completedSeconds)
        assertEquals(60L, result.totalSeconds)
    }

    // ── Test 12 ───────────────────────────────────────────────────────────────
    @Test
    fun `resume after restart shows only banked seconds with no live timer`() {
        // Server restarted: DB has finished entries only (no open entry) — timer is PAUSED
        val entries = listOf(
            finishedEntry(BASE, 120),
            finishedEntry(BASE.plusMinutes(30), 200)
        )

        val result = TimerAccumulator.accumulate(entries, BASE.plusHours(2))

        assertFalse(result.isRunning)
        assertEquals(0L, result.liveSeconds)
        assertEquals(320L, result.completedSeconds)
        assertEquals(320L, result.totalSeconds)
        assertNull(result.timerStartedAt)
    }

    // ── Test 13 ───────────────────────────────────────────────────────────────
    @Test
    fun `multiple pause and resume cycles accumulate all sessions correctly`() {
        // 3 finished sessions (10s + 20s + 30s = 60s banked) + 1 open session (15s live)
        val openStart = BASE.plusMinutes(60)
        val now = openStart.plusSeconds(15)
        val entries = listOf(
            finishedEntry(BASE, 10),
            finishedEntry(BASE.plusMinutes(20), 20),
            finishedEntry(BASE.plusMinutes(40), 30),
            openEntry(openStart)
        )

        val result = TimerAccumulator.accumulate(entries, now)

        assertEquals(60L, result.completedSeconds)
        assertEquals(15L, result.liveSeconds)
        assertEquals(75L, result.totalSeconds)
        assertTrue(result.isRunning)
    }

    // ── Test 14 ───────────────────────────────────────────────────────────────
    @Test
    fun `zero-duration guard coerces to zero when startTime equals now`() {
        val entries = listOf(openEntry(BASE))

        val result = TimerAccumulator.accumulate(entries, BASE)  // now == startTime → 0 s elapsed

        assertEquals(0L, result.liveSeconds, "coerceAtLeast(0) must prevent negative liveSeconds")
        assertTrue(result.isRunning)
        assertEquals(0L, result.totalSeconds)
    }

    // ── Test 15 ───────────────────────────────────────────────────────────────
    @Test
    fun `corrupt state with two open entries throws IllegalStateException`() {
        val entries = listOf(
            openEntry(BASE),
            openEntry(BASE.plusSeconds(30))
        )

        assertThrows<IllegalStateException> {
            TimerAccumulator.accumulate(entries, BASE.plusMinutes(5))
        }
    }
}
