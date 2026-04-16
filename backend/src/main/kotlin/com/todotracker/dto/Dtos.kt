package com.todotracker.dto

import com.todotracker.model.TaskStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

// ─── Task responses ───────────────────────────────────────────────────────────

data class TaskResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val status: TaskStatus,
    val createdAt: LocalDateTime,
    /** Seconds accumulated from all completed (stopped) sessions. */
    val completedSeconds: Long,
    /** Total elapsed seconds including any currently running session. */
    val totalSeconds: Long,
    /** True when a timer session is currently running. */
    val isTimerRunning: Boolean,
    /** The start time of the currently running session, or null. */
    val timerStartedAt: LocalDateTime?
)

// ─── Task requests ────────────────────────────────────────────────────────────

data class CreateTaskRequest(
    @field:NotBlank(message = "Title must not be blank")
    @field:Size(max = 255, message = "Title must be at most 255 characters")
    val title: String,

    @field:Size(max = 2000, message = "Description must be at most 2000 characters")
    val description: String? = null
)

data class UpdateTaskRequest(
    @field:Size(max = 255, message = "Title must be at most 255 characters")
    val title: String? = null,

    @field:Size(max = 2000, message = "Description must be at most 2000 characters")
    val description: String? = null,

    val status: TaskStatus? = null
)

// ─── Weekly summary ───────────────────────────────────────────────────────────

data class WeeklySummaryEntry(
    val taskId: Long,
    val taskTitle: String,
    val taskStatus: TaskStatus,
    /** Total tracked seconds for this task within the requested week. */
    val totalSeconds: Long
)

data class WeeklySummary(
    val weekStart: LocalDateTime,
    val weekEnd: LocalDateTime,
    val entries: List<WeeklySummaryEntry>,
    /** Grand total seconds across all tasks this week. */
    val grandTotalSeconds: Long
)

// ─── Error response ───────────────────────────────────────────────────────────

data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)
