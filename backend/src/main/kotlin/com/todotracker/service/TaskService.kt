package com.todotracker.service

import com.todotracker.dto.*
import com.todotracker.model.Task
import com.todotracker.model.TimeEntry
import com.todotracker.repository.TaskRepository
import com.todotracker.repository.TimeEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class TaskService(
    private val taskRepository: TaskRepository,
    private val timeEntryRepository: TimeEntryRepository
) {

    // ─── Task CRUD ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getAllTasks(): List<TaskResponse> =
        taskRepository.findAll()
            .sortedByDescending { it.createdAt }
            .map { buildResponse(it) }

    @Transactional(readOnly = true)
    fun getTaskById(id: Long): TaskResponse =
        buildResponse(findTask(id))

    @Transactional
    fun createTask(request: CreateTaskRequest): TaskResponse {
        val task = Task(title = request.title.trim(), description = request.description?.trim())
        return buildResponse(taskRepository.save(task))
    }

    @Transactional
    fun updateTask(id: Long, request: UpdateTaskRequest): TaskResponse {
        val task = findTask(id)
        request.title?.trim()?.let { task.title = it }
        // Allow explicitly setting description to null (clearing it)
        if (request.description != null) task.description = request.description.trim()
        request.status?.let { task.status = it }
        return buildResponse(taskRepository.save(task))
    }

    @Transactional
    fun deleteTask(id: Long) {
        findTask(id) // verify it exists
        // Stop any running timer first, then remove all entries, then the task
        val running = timeEntryRepository.findByTaskIdAndEndTimeIsNull(id)
        if (running != null) {
            running.endTime = LocalDateTime.now()
            running.durationSeconds = ChronoUnit.SECONDS.between(running.startTime, running.endTime)
            timeEntryRepository.save(running)
        }
        timeEntryRepository.deleteAllByTaskId(id)
        taskRepository.deleteById(id)
    }

    // ─── Timer operations ─────────────────────────────────────────────────────

    /**
     * Starts a new timer session for the task.
     * Throws if a session is already running (idempotency guard).
     */
    @Transactional
    fun startTimer(id: Long): TaskResponse {
        val task = findTask(id)

        val existing = timeEntryRepository.findByTaskIdAndEndTimeIsNull(id)
        if (existing != null) {
            throw IllegalStateException("Timer is already running for task '$id'. Stop it before starting a new session.")
        }

        val entry = TimeEntry(task = task, startTime = LocalDateTime.now())
        timeEntryRepository.save(entry)
        return buildResponse(task)
    }

    /**
     * Stops the currently running timer session for the task.
     * Accurately computes elapsed seconds from the recorded startTime
     * rather than relying on client-side values.
     * Throws if no timer is running.
     */
    @Transactional
    fun stopTimer(id: Long): TaskResponse {
        val task = findTask(id)

        val running = timeEntryRepository.findByTaskIdAndEndTimeIsNull(id)
            ?: throw IllegalStateException("No running timer for task '$id'. Start a timer first.")

        val now = LocalDateTime.now()
        running.endTime = now
        running.durationSeconds = ChronoUnit.SECONDS.between(running.startTime, now)
            .coerceAtLeast(0) // guard against clock skew
        timeEntryRepository.save(running)

        return buildResponse(task)
    }

    // ─── Weekly summary ───────────────────────────────────────────────────────

    /**
     * Returns a time summary for the week starting at [weekStart].
     * Entries whose startTime falls within [weekStart, weekStart + 7 days) are included.
     * Running timers are counted up to the current moment.
     */
    @Transactional(readOnly = true)
    fun getWeeklySummary(weekStart: LocalDateTime): WeeklySummary {
        val weekEnd = weekStart.plusWeeks(1)
        val entries = timeEntryRepository.findEntriesInRange(weekStart, weekEnd)
        val now = LocalDateTime.now()

        // Group by task, accumulate seconds
        data class TaskAccumulator(val title: String, val status: com.todotracker.model.TaskStatus, var seconds: Long = 0)
        val accumulator = mutableMapOf<Long, TaskAccumulator>()

        for (entry in entries) {
            val taskId = entry.task.id
            val elapsed = if (entry.endTime != null) {
                entry.durationSeconds
            } else {
                // Timer still running; count to now, clamped to week boundary
                val effectiveEnd = if (now < weekEnd) now else weekEnd
                ChronoUnit.SECONDS.between(entry.startTime, effectiveEnd).coerceAtLeast(0)
            }
            val acc = accumulator.getOrPut(taskId) {
                TaskAccumulator(entry.task.title, entry.task.status)
            }
            acc.seconds += elapsed
        }

        val summaryEntries = accumulator.map { (taskId, acc) ->
            WeeklySummaryEntry(
                taskId = taskId,
                taskTitle = acc.title,
                taskStatus = acc.status,
                totalSeconds = acc.seconds
            )
        }.sortedByDescending { it.totalSeconds }

        return WeeklySummary(
            weekStart = weekStart,
            weekEnd = weekEnd,
            entries = summaryEntries,
            grandTotalSeconds = summaryEntries.sumOf { it.totalSeconds }
        )
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun findTask(id: Long): Task =
        taskRepository.findById(id).orElseThrow {
            NoSuchElementException("Task with id=$id not found")
        }

    /**
     * Builds a TaskResponse for the given task.
     * - completedSeconds: sum of durationSeconds from all finished entries
     * - totalSeconds: completedSeconds + elapsed seconds of any running entry
     * - timerStartedAt: start time of the running entry, or null
     */
    private fun buildResponse(task: Task): TaskResponse {
        val entries = timeEntryRepository.findAllByTaskId(task.id)
        val now = LocalDateTime.now()

        var completedSeconds = 0L
        var runningEntry: TimeEntry? = null

        for (entry in entries) {
            if (entry.endTime != null) {
                completedSeconds += entry.durationSeconds
            } else {
                runningEntry = entry
            }
        }

        val liveSeconds = runningEntry?.let {
            ChronoUnit.SECONDS.between(it.startTime, now).coerceAtLeast(0)
        } ?: 0L

        return TaskResponse(
            id = task.id,
            title = task.title,
            description = task.description,
            status = task.status,
            createdAt = task.createdAt,
            completedSeconds = completedSeconds,
            totalSeconds = completedSeconds + liveSeconds,
            isTimerRunning = runningEntry != null,
            timerStartedAt = runningEntry?.startTime
        )
    }
}
