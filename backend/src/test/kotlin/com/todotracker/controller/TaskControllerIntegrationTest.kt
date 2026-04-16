package com.todotracker.controller

import com.todotracker.dto.CreateTaskRequest
import com.todotracker.dto.ErrorResponse
import com.todotracker.dto.TaskResponse
import com.todotracker.dto.WeeklySummary
import com.todotracker.model.TaskStatus
import com.todotracker.repository.TaskRepository
import com.todotracker.repository.TimeEntryRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/**
 * HTTP-level integration tests for [TaskController].
 *
 * Boots the full Spring context on a random port and exercises every endpoint
 * through [TestRestTemplate], verifying status codes, response bodies, and
 * error payloads exactly as a real client would see them.
 *
 * No @Transactional — RANDOM_PORT runs the server in a separate thread.
 * State is reset between tests via repository.deleteAll() calls in @BeforeEach.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskControllerIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var taskRepository: TaskRepository

    @Autowired
    lateinit var timeEntryRepository: TimeEntryRepository

    @BeforeEach
    fun cleanDatabase() {
        // FK constraint: time entries must be deleted before tasks
        timeEntryRepository.deleteAll()
        taskRepository.deleteAll()
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private fun createTaskViaHttp(title: String = "Test task"): TaskResponse {
        val request = CreateTaskRequest(title = title)
        val response: ResponseEntity<TaskResponse> = restTemplate.postForEntity(
            "/api/tasks",
            request,
            TaskResponse::class.java
        )
        assertEquals(HttpStatus.CREATED, response.statusCode,
            "Expected 201 CREATED when creating task with title='$title'")
        return response.body!!
    }

    // =========================================================================
    // Test 1 — POST /api/tasks → 201 with correct default fields
    // =========================================================================

    @Test
    fun `POST tasks returns 201 with correct defaults`() {
        val response: ResponseEntity<TaskResponse> = restTemplate.postForEntity(
            "/api/tasks",
            CreateTaskRequest(title = "My first task", description = "Some description"),
            TaskResponse::class.java
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = response.body!!
        assertEquals("My first task", body.title)
        assertEquals("Some description", body.description)
        assertEquals(TaskStatus.TODO, body.status)
        assertFalse(body.isTimerRunning)
        assertEquals(0L, body.completedSeconds)
        assertEquals(0L, body.totalSeconds)
        assertNull(body.timerStartedAt)
        assertTrue(body.id > 0)
        assertNotNull(body.createdAt)
    }

    // =========================================================================
    // Test 2 — POST /api/tasks with blank title → 400
    // =========================================================================

    @Test
    fun `POST tasks with blank title returns 400`() {
        val response: ResponseEntity<ErrorResponse> = restTemplate.postForEntity(
            "/api/tasks",
            CreateTaskRequest(title = "   "),
            ErrorResponse::class.java
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body!!
        assertEquals(400, body.status)
        assertEquals("Validation Failed", body.error)
        assertTrue(body.message.isNotBlank(),
            "Error message must describe the validation failure")
    }

    // =========================================================================
    // Test 3 — GET /api/tasks with 2 tasks → 200, size=2, newest first
    // =========================================================================

    @Test
    fun `GET tasks returns all tasks newest first`() {
        createTaskViaHttp("Alpha task")
        createTaskViaHttp("Beta task")

        val response: ResponseEntity<Array<TaskResponse>> = restTemplate.getForEntity(
            "/api/tasks",
            Array<TaskResponse>::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val tasks = response.body!!
        assertEquals(2, tasks.size)
        // Newest-first: Beta was created second, so it appears at index 0
        assertEquals("Beta task", tasks[0].title)
        assertEquals("Alpha task", tasks[1].title)
    }

    // =========================================================================
    // Test 4 — GET /api/tasks/{id} → 200, correct id and title
    // =========================================================================

    @Test
    fun `GET tasks by id returns correct task`() {
        val created = createTaskViaHttp("Specific task")

        val response: ResponseEntity<TaskResponse> = restTemplate.getForEntity(
            "/api/tasks/${created.id}",
            TaskResponse::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals(created.id, body.id)
        assertEquals("Specific task", body.title)
        assertEquals(TaskStatus.TODO, body.status)
    }

    // =========================================================================
    // Test 5 — GET /api/tasks/99999 → 404
    // =========================================================================

    @Test
    fun `GET tasks with nonexistent id returns 404`() {
        val response: ResponseEntity<ErrorResponse> = restTemplate.getForEntity(
            "/api/tasks/99999",
            ErrorResponse::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        val body = response.body!!
        assertEquals(404, body.status)
        assertEquals("Not Found", body.error)
    }

    // =========================================================================
    // Test 6 — POST /api/tasks/{id}/start → 200, timer running
    // =========================================================================

    @Test
    fun `POST start returns 200 with timer running`() {
        val created = createTaskViaHttp("Task to time")

        val response: ResponseEntity<TaskResponse> = restTemplate.postForEntity(
            "/api/tasks/${created.id}/start",
            null,
            TaskResponse::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertTrue(body.isTimerRunning)
        assertNotNull(body.timerStartedAt)
        assertEquals(created.id, body.id)
    }

    // =========================================================================
    // Test 7 — POST /api/tasks/{id}/start twice → 409 on second start
    // =========================================================================

    @Test
    fun `POST start twice returns 409 on second call`() {
        val created = createTaskViaHttp("Task for double start")

        // First start — must succeed
        val firstResponse: ResponseEntity<TaskResponse> = restTemplate.postForEntity(
            "/api/tasks/${created.id}/start",
            null,
            TaskResponse::class.java
        )
        assertEquals(HttpStatus.OK, firstResponse.statusCode)

        // Second start — must return 409 Conflict
        val secondResponse: ResponseEntity<ErrorResponse> = restTemplate.postForEntity(
            "/api/tasks/${created.id}/start",
            null,
            ErrorResponse::class.java
        )
        assertEquals(HttpStatus.CONFLICT, secondResponse.statusCode)
        val body = secondResponse.body!!
        assertEquals(409, body.status)
        assertEquals("Conflict", body.error)
    }

    // =========================================================================
    // Test 8 — POST /api/tasks/{id}/stop after start → 200, timer stopped
    // =========================================================================

    @Test
    fun `POST stop after start returns 200 with timer stopped`() {
        val created = createTaskViaHttp("Task to stop")
        restTemplate.postForEntity("/api/tasks/${created.id}/start", null, TaskResponse::class.java)

        val response: ResponseEntity<TaskResponse> = restTemplate.postForEntity(
            "/api/tasks/${created.id}/stop",
            null,
            TaskResponse::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertFalse(body.isTimerRunning)
        assertNull(body.timerStartedAt)
        assertTrue(body.completedSeconds >= 0L)
        assertEquals(body.completedSeconds, body.totalSeconds)
    }

    // =========================================================================
    // Test 9 — POST /api/tasks/{id}/stop on idle task → 409
    // =========================================================================

    @Test
    fun `POST stop on idle task returns 409`() {
        val created = createTaskViaHttp("Idle task")

        val response: ResponseEntity<ErrorResponse> = restTemplate.postForEntity(
            "/api/tasks/${created.id}/stop",
            null,
            ErrorResponse::class.java
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        val body = response.body!!
        assertEquals(409, body.status)
        assertEquals("Conflict", body.error)
    }

    // =========================================================================
    // Test 10 — DELETE /api/tasks/{id} with running timer → 204, then 404 on GET
    // =========================================================================

    @Test
    fun `DELETE task with running timer returns 204 and task is gone`() {
        val created = createTaskViaHttp("Task to delete")
        restTemplate.postForEntity("/api/tasks/${created.id}/start", null, TaskResponse::class.java)

        // DELETE must return 204 No Content without error
        val deleteResponse: ResponseEntity<Void> = restTemplate.exchange(
            "/api/tasks/${created.id}",
            HttpMethod.DELETE,
            HttpEntity.EMPTY,
            Void::class.java
        )
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.statusCode)

        // Subsequent GET must return 404
        val getResponse: ResponseEntity<ErrorResponse> = restTemplate.getForEntity(
            "/api/tasks/${created.id}",
            ErrorResponse::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, getResponse.statusCode)
    }

    // =========================================================================
    // Test 11 — GET /api/summary/weekly after start+stop → 200, grandTotalSeconds >= 0
    // =========================================================================

    @Test
    fun `GET summary weekly returns 200 with non-negative grandTotalSeconds`() {
        val created = createTaskViaHttp("Tracked task")
        restTemplate.postForEntity("/api/tasks/${created.id}/start", null, TaskResponse::class.java)
        restTemplate.postForEntity("/api/tasks/${created.id}/stop", null, TaskResponse::class.java)

        val response: ResponseEntity<WeeklySummary> = restTemplate.getForEntity(
            "/api/summary/weekly",
            WeeklySummary::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertTrue(body.grandTotalSeconds >= 0L)
        assertNotNull(body.weekStart)
        assertNotNull(body.weekEnd)
        assertNotNull(body.entries)
    }
}
