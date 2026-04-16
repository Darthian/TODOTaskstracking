# TODO Tracker — Backend

REST API for the TODO Tracker with time tracking. Built with Kotlin 1.9 + Spring Boot 3.2, backed by an H2 in-memory database.

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.8+ (or use the wrapper if present) |

Verify your setup:

```bash
java -version   # must report 17 or higher
mvn -version
```

## Running the application

```bash
cd backend
mvn spring-boot:run
```

The server starts on **http://localhost:8080** with the `dev` Spring profile active by default.

> **Note:** The H2 database is in-memory. All data is lost on every restart.

### Build and run the JAR instead

```bash
mvn clean package -DskipTests
java -jar target/todo-tracker-0.0.1-SNAPSHOT.jar
```

## Configuration

### `src/main/resources/application.yml` (base config)

```yaml
spring:
  profiles:
    active: dev          # activates application-dev.yml automatically
  jackson:
    serialization:
      write-dates-as-timestamps: false   # dates as ISO strings
    deserialization:
      fail-on-unknown-properties: false
    time-zone: UTC

server:
  port: 8080
```

### `src/main/resources/application-dev.yml` (dev profile)

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:todotracker;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driver-class-name: org.h2.Driver
    username: sa
    password:             # no password
  h2:
    console:
      enabled: true
      path: /h2-console   # browser UI at http://localhost:8080/h2-console
  jpa:
    hibernate:
      ddl-auto: create-drop   # schema is recreated on each start
    show-sql: false
```

### CORS

Only `http://localhost:3000` is allowed by default (configured in `WebConfig.kt`). To allow a different frontend origin, update the `allowedOrigins` value there.

## H2 console (dev only)

Browse the in-memory database at **http://localhost:8080/h2-console**.

| Field | Value |
|-------|-------|
| JDBC URL | `jdbc:h2:mem:todotracker` |
| User name | `sa` |
| Password | *(leave blank)* |

## API reference

Base path: `/api`

### Tasks

| Method | Path | Description | Success |
|--------|------|-------------|---------|
| `GET` | `/tasks` | List all tasks (newest first) | 200 |
| `POST` | `/tasks` | Create a task | 201 |
| `GET` | `/tasks/{id}` | Get a task by ID | 200 |
| `PUT` | `/tasks/{id}` | Update title / description / status | 200 |
| `DELETE` | `/tasks/{id}` | Delete a task (stops any running timer first) | 204 |

### Timer

| Method | Path | Description | Error |
|--------|------|-------------|-------|
| `POST` | `/tasks/{id}/start` | Start a timer session | 409 if already running |
| `POST` | `/tasks/{id}/stop` | Stop the running session | 409 if not running |

### Summary

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/summary/weekly` | Weekly summary. Optional `?weekStart=2024-01-08T00:00:00`; defaults to the current ISO week (Monday 00:00 UTC). |

### Request bodies

**POST /tasks**
```json
{
  "title": "My task",
  "description": "Optional description"
}
```

**PUT /tasks/{id}**
```json
{
  "title": "Updated title",
  "description": "Updated description",
  "status": "IN_PROGRESS"
}
```
All fields are optional; only provided fields are updated. Valid `status` values: `TODO`, `IN_PROGRESS`, `DONE`.

### Response shape (`TaskResponse`)

```json
{
  "id": 1,
  "title": "My task",
  "description": "Optional description",
  "status": "TODO",
  "createdAt": "2024-01-08T10:00:00",
  "completedSeconds": 120,
  "totalSeconds": 185,
  "isTimerRunning": true,
  "timerStartedAt": "2024-01-08T10:02:00"
}
```

| Field | Description |
|-------|-------------|
| `completedSeconds` | Sum of all finished timer sessions |
| `totalSeconds` | `completedSeconds` + live elapsed seconds of any running session |
| `isTimerRunning` | `true` when a session has no end time |
| `timerStartedAt` | Start time of the active session, or `null` |

### Error responses

| HTTP status | Cause |
|-------------|-------|
| 400 | Validation failure (missing required field, etc.) |
| 404 | Task not found |
| 409 | Timer conflict (already running / not running) |
| 500 | Unexpected server error |

## Project structure

```
backend/src/main/kotlin/com/todotracker/
  TodoTrackerApplication.kt          – entry point (@SpringBootApplication)
  config/WebConfig.kt                – global CORS configuration
  controller/TaskController.kt       – HTTP layer only (no business logic)
  dto/Dtos.kt                        – request / response data classes
  exception/GlobalExceptionHandler.kt – maps exceptions to HTTP status codes
  model/Task.kt                      – Task JPA entity
  model/TimeEntry.kt                 – TimeEntry JPA entity (one row per session)
  repository/TaskRepository.kt
  repository/TimeEntryRepository.kt
  service/TaskService.kt             – all business logic + timer rules
```

## Quick smoke test

Once the server is running, try these commands:

```bash
# Create a task
curl -s -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"Test task"}' | jq .

# List all tasks
curl -s http://localhost:8080/api/tasks | jq .

# Start timer (use the id returned above)
curl -s -X POST http://localhost:8080/api/tasks/1/start | jq .

# Stop timer
curl -s -X POST http://localhost:8080/api/tasks/1/stop | jq .

# Weekly summary
curl -s http://localhost:8080/api/summary/weekly | jq .
```
