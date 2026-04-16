<!--
Create a project for taks tracking using a BE in Kotlin and the FE in NextJs. It will support start/stop timers and correctly accumulates time across pauses and resumed sessions, with attention to edge cases.

Create a skill named 'timer-logic-scaffold' that given a task entity, generate the start/stop/pause/resume timer state machine, accumulator function, and unit tests for boundary conditions (pause at midnight, resume after restart, etc.).
-->

# Task Tracker — TODO app with time tracking

## Stack

| Layer    | Technology                                      |
|----------|-------------------------------------------------|
| Backend  | Kotlin 2.1 · Spring Boot 3.2 · H2 (in-memory)  |
| Frontend | React 18 · TypeScript · plain CSS               |
| E2E      | Playwright 1.44 (Chromium)                      |
| CI       | GitHub Actions                                  |

---

## Running locally (without Docker)

### Backend

**Requirements:** Java 17+, Maven 3.8+

```bash
cd backend
mvn spring-boot:run
```

Server starts on **http://localhost:8080** with the `dev` profile (H2 in-memory, data resets on restart).

> H2 console: http://localhost:8080/h2-console  
> JDBC URL: `jdbc:h2:mem:todotracker` · User: `sa` · Password: *(empty)*

### Frontend

**Requirements:** Node 18+, npm 9+

```bash
cd frontend
npm install
npm start
```

Opens on **http://localhost:3000**. The `/api` proxy is configured in `package.json` — the backend must be running first.

---

## Running with Docker Compose

**Requirements:** Docker Desktop (or Docker Engine + Compose plugin)

```bash
# Build images and start backend + frontend
docker compose up --build

# Stop and remove containers
docker compose down
```

| Service  | Host URL                   |
|----------|----------------------------|
| Frontend | http://localhost:3000      |
| Backend  | http://localhost:8080      |

The frontend image (nginx) proxies `/api/*` requests to the backend service automatically — no backend URL config needed in the browser.

---

## Testing

### Test layers

| Layer | Tool | What it covers |
|-------|------|----------------|
| Backend unit tests | JUnit 5 + Mockito-Kotlin | `TaskService`, `TimerStateMachine`, `TimerAccumulator` — repositories mocked |
| Backend integration tests | `@SpringBootTest` (H2) | Full service layer against real H2; also HTTP-level via `TestRestTemplate` |
| Frontend unit tests | Jest + React Testing Library | `useTimer` hook logic and `formatDuration` |
| E2E integration tests | Playwright (Chromium) | Full browser tests against the live stack |

### Running backend tests

```bash
cd backend
mvn test
```

**35 tests** across three suites — all run in-process against H2; no server needed.

| Suite | Tests | Coverage |
|-------|-------|----------|
| `TimerStateMachineTest` | 15 | State machine transitions + `TimerAccumulator` boundary conditions |
| `TaskServiceTest` | 13 | Timer logic unit tests (mocked repos) |
| `TaskServiceIntegrationTest` | 7 | Full lifecycle against H2 |
| `TaskControllerIntegrationTest` | 11 | HTTP status codes and response shapes via `TestRestTemplate` |

### Running frontend tests

```bash
cd frontend
npm test              # interactive watch mode
npm test -- --watchAll=false   # single run (CI mode)
```

Covers `formatDuration` (9 parameterized cases) and `useTimer` (5 behavioural cases).

### Running E2E tests locally

The E2E tests require the full stack to be running. Start with Docker Compose, then run Playwright in a second terminal.

```bash
# Terminal 1 — start the stack
docker compose up --build

# Terminal 2 — install and run E2E tests
cd e2e
npm install
npx playwright install chromium   # first time only
npx playwright test               # headless
npx playwright test --headed      # with browser visible
npx playwright test --ui          # Playwright interactive UI
```

The Playwright config reads `BASE_URL` from the environment (defaults to `http://localhost:3000`).

**E2E test suites:**

| File | Tests | Scenarios |
|------|-------|-----------|
| `task-lifecycle.spec.ts` | 7 | Create, read, edit, delete tasks; tab navigation |
| `timer-operations.spec.ts` | 5 | Start/stop, pause/resume accumulation, running badge count, delete with active timer, weekly summary |

### Running integration tests with Docker Compose (CI mode)

This is exactly what the CI pipeline runs — builds both images from scratch and executes the Playwright suite inside a container:

```bash
docker compose -f docker-compose.test.yml up \
  --build \
  --abort-on-container-exit \
  --exit-code-from e2e
```

Exit code mirrors the Playwright result: `0` = all green, `1` = failures.

Clean up after the run:

```bash
docker compose -f docker-compose.test.yml down --volumes
```

On failure, Playwright saves a report to `e2e/playwright-report/`:

```bash
cd e2e && npx playwright show-report
```

---

## CI pipeline (GitHub Actions)

The workflow at `.github/workflows/ci.yml` runs on every push to `main`/`develop` and on all pull requests.

```
backend-tests ──┐
                ├──► integration-tests  (Docker Compose + Playwright)
frontend-tests ─┘
```

| Job | Runner | What runs |
|-----|--------|-----------|
| `backend-tests` | ubuntu-latest | `mvn test` — all 46 backend tests |
| `frontend-tests` | ubuntu-latest | `npm test -- --watchAll=false --ci` |
| `integration-tests` | ubuntu-latest | `docker compose -f docker-compose.test.yml up …` — full E2E suite |

The E2E job only runs after both unit-test jobs pass. Maven and npm dependencies are cached between runs.

On failure, the CI uploads:
- **Surefire XML reports** (`backend-surefire-reports` artifact)
- **Playwright HTML report** with screenshots and videos (`playwright-report` artifact)

---

## API reference

| Method | Path                       | Description                               |
|--------|----------------------------|-------------------------------------------|
| GET    | `/api/tasks`               | List all tasks (newest first)             |
| POST   | `/api/tasks`               | Create `{ title, description? }` → 201   |
| GET    | `/api/tasks/{id}`          | Get task by ID                            |
| PUT    | `/api/tasks/{id}`          | Update `{ title?, description?, status? }` |
| DELETE | `/api/tasks/{id}`          | Delete (stops running timer first) → 204  |
| POST   | `/api/tasks/{id}/start`    | Start timer session → 409 if running      |
| POST   | `/api/tasks/{id}/stop`     | Stop running session → 409 if idle        |
| GET    | `/api/summary/weekly`      | Weekly summary (`?weekStart=ISO_DATE_TIME`) |

### Timer edge cases handled

- **Start while running** → `409 Conflict` (prevents double-counting)
- **Stop while idle** → `409 Conflict`
- **Multiple pause/resume cycles** → each cycle is a separate `TimeEntry`; durations accumulate correctly
- **Delete with running timer** → timer stopped and duration persisted before deletion
- **Live display** → frontend uses `timerStartedAt` from the server to tick locally; no extra polling

---

## Project structure

```
.
├── .github/
│   └── workflows/
│       └── ci.yml                 # GitHub Actions CI pipeline
├── backend/
│   ├── Dockerfile                 # Multi-stage Maven → JRE 17 Alpine
│   ├── pom.xml
│   └── src/
│       ├── main/kotlin/com/todotracker/
│       │   ├── controller/TaskController.kt
│       │   ├── dto/Dtos.kt
│       │   ├── exception/GlobalExceptionHandler.kt
│       │   ├── model/{Task,TimeEntry}.kt
│       │   ├── repository/{Task,TimeEntry}Repository.kt
│       │   └── service/{TaskService,TimerStateMachine,TimerAccumulator}.kt
│       └── test/kotlin/com/todotracker/
│           ├── controller/TaskControllerIntegrationTest.kt
│           └── service/{TaskServiceTest,TaskServiceIntegrationTest,TimerStateMachineTest}.kt
├── e2e/
│   ├── package.json               # Playwright dependency
│   ├── playwright.config.ts
│   └── tests/
│       ├── task-lifecycle.spec.ts
│       └── timer-operations.spec.ts
├── frontend/
│   ├── Dockerfile                 # Multi-stage Node → nginx Alpine
│   ├── nginx.conf                 # Proxies /api/* to backend service
│   └── src/
│       ├── api/client.ts
│       ├── components/{CreateTask,TaskItem,TaskList,WeeklySummary}.tsx
│       ├── hooks/useTimer.ts
│       ├── hooks/useTimer.test.ts
│       ├── setupTests.ts
│       └── types/index.ts
├── docker-compose.yml             # Dev: backend + frontend
└── docker-compose.test.yml        # CI: backend + frontend + e2e runner
```
