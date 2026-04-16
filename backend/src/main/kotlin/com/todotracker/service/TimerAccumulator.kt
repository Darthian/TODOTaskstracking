package com.todotracker.service

import com.todotracker.model.TimeEntry
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Snapshot of accumulated time for a task, computed from its [TimeEntry] list.
 *
 * @property completedSeconds sum of [TimeEntry.durationSeconds] for all finished entries
 * @property liveSeconds      elapsed seconds of the currently-running entry (0 if IDLE or PAUSED)
 * @property totalSeconds     [completedSeconds] + [liveSeconds]
 * @property isRunning        true when there is an open entry (endTime == null)
 * @property timerStartedAt   [TimeEntry.startTime] of the running entry, or null
 */
data class AccumulatorResult(
    val completedSeconds: Long,
    val liveSeconds: Long,
    val totalSeconds: Long,
    val isRunning: Boolean,
    val timerStartedAt: LocalDateTime?
)

/**
 * Pure accumulation logic — no Spring beans, no I/O.
 *
 * Call [accumulate] with the full list of [TimeEntry] rows for a single task.
 * The caller is responsible for fetching the entries; this object only does math.
 *
 * Design notes:
 * - Clock is injected via [now] so boundary conditions (e.g. midnight) can be tested deterministically.
 * - [ChronoUnit.SECONDS.between] handles cross-day and cross-month spans correctly.
 * - [coerceAtLeast](0) guards against negative values from clock skew or test-fixture mistakes.
 * - More than one open entry is a corrupt state and throws immediately.
 */
object TimerAccumulator {

    fun accumulate(
        entries: List<TimeEntry>,
        now: LocalDateTime = LocalDateTime.now()
    ): AccumulatorResult {

        val openEntries = entries.filter { it.endTime == null }
        if (openEntries.size > 1) {
            throw IllegalStateException(
                "Corrupt timer state: ${openEntries.size} open entries found — at most 1 allowed"
            )
        }

        val completedSeconds = entries
            .filter { it.endTime != null }
            .sumOf { it.durationSeconds }

        val openEntry = openEntries.firstOrNull()
        val liveSeconds = openEntry
            ?.let { ChronoUnit.SECONDS.between(it.startTime, now).coerceAtLeast(0) }
            ?: 0L

        return AccumulatorResult(
            completedSeconds = completedSeconds,
            liveSeconds = liveSeconds,
            totalSeconds = completedSeconds + liveSeconds,
            isRunning = openEntry != null,
            timerStartedAt = openEntry?.startTime
        )
    }
}
