package com.todotracker.controller

import com.todotracker.dto.*
import com.todotracker.service.TaskService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

@RestController
@RequestMapping("/api")
class TaskController(private val taskService: TaskService) {

    // ─── Task CRUD ────────────────────────────────────────────────────────────

    @GetMapping("/tasks")
    fun getAllTasks(): ResponseEntity<List<TaskResponse>> =
        ResponseEntity.ok(taskService.getAllTasks())

    @GetMapping("/tasks/{id}")
    fun getTaskById(@PathVariable id: Long): ResponseEntity<TaskResponse> =
        ResponseEntity.ok(taskService.getTaskById(id))

    @PostMapping("/tasks")
    fun createTask(@Valid @RequestBody request: CreateTaskRequest): ResponseEntity<TaskResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(taskService.createTask(request))

    @PutMapping("/tasks/{id}")
    fun updateTask(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateTaskRequest
    ): ResponseEntity<TaskResponse> =
        ResponseEntity.ok(taskService.updateTask(id, request))

    @DeleteMapping("/tasks/{id}")
    fun deleteTask(@PathVariable id: Long): ResponseEntity<Void> {
        taskService.deleteTask(id)
        return ResponseEntity.noContent().build()
    }

    // ─── Timer controls ───────────────────────────────────────────────────────

    /**
     * POST /api/tasks/{id}/start
     * Begins a new timer session. Returns 409 if a session is already running.
     */
    @PostMapping("/tasks/{id}/start")
    fun startTimer(@PathVariable id: Long): ResponseEntity<TaskResponse> =
        ResponseEntity.ok(taskService.startTimer(id))

    /**
     * POST /api/tasks/{id}/stop
     * Stops the running timer session. Returns 409 if no session is active.
     */
    @PostMapping("/tasks/{id}/stop")
    fun stopTimer(@PathVariable id: Long): ResponseEntity<TaskResponse> =
        ResponseEntity.ok(taskService.stopTimer(id))

    // ─── Weekly summary ───────────────────────────────────────────────────────

    /**
     * GET /api/summary/weekly?weekStart=2024-01-08T00:00:00
     * If weekStart is omitted, defaults to the current ISO week (Monday 00:00).
     */
    @GetMapping("/summary/weekly")
    fun getWeeklySummary(
        @RequestParam(required = false) weekStart: String?
    ): ResponseEntity<WeeklySummary> {
        val start: LocalDateTime = if (weekStart != null) {
            LocalDateTime.parse(weekStart)
        } else {
            LocalDateTime.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate()
                .atStartOfDay()
        }
        return ResponseEntity.ok(taskService.getWeeklySummary(start))
    }
}
