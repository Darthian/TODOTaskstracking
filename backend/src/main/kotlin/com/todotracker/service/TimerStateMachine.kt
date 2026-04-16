package com.todotracker.service

import com.todotracker.model.Task
import com.todotracker.model.TimeEntry
import com.todotracker.repository.TimeEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class TimerState { IDLE, RUNNING, PAUSED }

/**
 * State machine for task timers.
 *
 * States (derived from TimeEntry rows, never stored explicitly):
 *   IDLE    – no TimeEntry rows exist for this task (fresh start)
 *   RUNNING – exactly one TimeEntry with endTime == null
 *   PAUSED  – one or more TimeEntry rows exist, all with endTime set (time is banked)
 *
 * Valid transitions:
 *   IDLE    → start  → RUNNING
 *   RUNNING → stop   → IDLE    (closes open entry, banks time, no new entry created)
 *   RUNNING → pause  → PAUSED  (same close operation as stop)
 *   PAUSED  → resume → RUNNING (opens a new TimeEntry; prior banked time is preserved)
 *   PAUSED  → stop   → PAUSED  (no-op: nothing is running, time is already banked)
 *
 * Guards (throw IllegalStateException on violation):
 *   start  – blocked when RUNNING
 *   pause  – blocked when not RUNNING (IDLE or PAUSED)
 *   resume – blocked when RUNNING, and when IDLE (nothing to resume)
 */
@Service
class TimerStateMachine(private val repo: TimeEntryRepository) {

    /**
     * Derives the timer state for [taskId] from repository data.
     * Prefer direct repo calls inside transition methods to avoid redundant queries.
     */
    fun currentState(taskId: Long): TimerState {
        if (repo.findByTaskIdAndEndTimeIsNull(taskId) != null) return TimerState.RUNNING
        return if (repo.findAllByTaskId(taskId).isEmpty()) TimerState.IDLE else TimerState.PAUSED
    }

    /**
     * Starts a new timer session from IDLE.
     * Throws [IllegalStateException] if already RUNNING.
     *
     * @param clock injectable timestamp — defaults to [LocalDateTime.now] for production, override in tests
     */
    @Transactional
    fun start(taskId: Long, task: Task, clock: LocalDateTime = LocalDateTime.now()): TimeEntry {
        if (repo.findByTaskIdAndEndTimeIsNull(taskId) != null) {
            throw IllegalStateException("Timer is already running for task $taskId — stop or pause it first")
        }
        return repo.save(TimeEntry(task = task, startTime = clock))
    }

    /**
     * Stops the running timer, persisting elapsed seconds to [TimeEntry.durationSeconds].
     * If no timer is running (IDLE or PAUSED), this is a safe no-op and returns null.
     *
     * @param clock injectable timestamp
     */
    @Transactional
    fun stop(taskId: Long, clock: LocalDateTime = LocalDateTime.now()): TimeEntry? {
        val openEntry = repo.findByTaskIdAndEndTimeIsNull(taskId) ?: return null
        return closeEntry(openEntry, clock)
    }

    /**
     * Pauses the running timer, banking the current session's elapsed seconds.
     * Identical to [stop] in its effect on the open [TimeEntry], but semantically
     * signals intent to resume later.
     * Throws [IllegalStateException] if not RUNNING (IDLE or already PAUSED).
     *
     * @param clock injectable timestamp
     */
    @Transactional
    fun pause(taskId: Long, clock: LocalDateTime = LocalDateTime.now()): TimeEntry {
        val openEntry = repo.findByTaskIdAndEndTimeIsNull(taskId)
            ?: run {
                val reason = if (repo.findAllByTaskId(taskId).isEmpty()) "never started" else "already paused"
                throw IllegalStateException("Cannot pause task $taskId: timer is $reason")
            }
        return closeEntry(openEntry, clock)
    }

    /**
     * Resumes a PAUSED timer by opening a new [TimeEntry].
     * Prior banked time is preserved across existing closed entries.
     * Throws [IllegalStateException] if RUNNING (already active) or IDLE (nothing to resume — use [start]).
     *
     * @param clock injectable timestamp
     */
    @Transactional
    fun resume(taskId: Long, task: Task, clock: LocalDateTime = LocalDateTime.now()): TimeEntry {
        if (repo.findByTaskIdAndEndTimeIsNull(taskId) != null) {
            throw IllegalStateException("Timer is already running for task $taskId — pause it before resuming")
        }
        if (repo.findAllByTaskId(taskId).isEmpty()) {
            throw IllegalStateException("Cannot resume task $taskId: no prior sessions — use start() instead")
        }
        return repo.save(TimeEntry(task = task, startTime = clock))
    }

    // ─── Private ──────────────────────────────────────────────────────────────

    private fun closeEntry(entry: TimeEntry, clock: LocalDateTime): TimeEntry {
        entry.endTime = clock
        entry.durationSeconds = ChronoUnit.SECONDS.between(entry.startTime, clock).coerceAtLeast(0)
        return repo.save(entry)
    }
}
