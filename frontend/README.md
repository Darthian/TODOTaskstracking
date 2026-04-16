# TODO Tracker — Frontend

React 18 + TypeScript UI for the TODO Tracker with time tracking. Built with Create React App (`react-scripts 5`) and plain CSS — no UI component library.

## Prerequisites

| Tool | Version |
|------|---------|
| Node.js | 16+ (18 LTS recommended) |
| npm | 8+ (bundled with Node) |

Verify your setup:

```bash
node -v   # must report v16 or higher
npm -v
```

> The backend must also be running on **http://localhost:8080** — all `/api` calls are proxied there automatically. See `backend/README.md` for backend setup.

## Running the application

```bash
cd frontend
npm install      # first time only — installs dependencies
npm start
```

The dev server starts on **http://localhost:3000** and opens the browser automatically. Changes to source files hot-reload without a full refresh.

## Building for production

```bash
npm run build
```

Outputs an optimized static bundle to `frontend/build/`. Serve it with any static file server or behind a reverse proxy that forwards `/api` requests to the backend.

## Configuration

### API proxy (`package.json`)

```json
"proxy": "http://localhost:8080"
```

During development, the CRA dev server forwards every request that does not match a static asset (i.e., all `/api/*` calls) to the Spring Boot backend on port 8080. **No CORS headers are needed in development because of this proxy.**

If your backend runs on a different port, change this value and restart the dev server.

### TypeScript (`tsconfig.json`)

TypeScript strict mode is enabled (`"strict": true`). All type errors are treated as build errors. Target is `es5` with `esnext` module resolution.

## Project structure

```
frontend/src/
  index.tsx                   – ReactDOM.createRoot entry point
  index.css                   – global reset (minimal)
  App.tsx                     – tab navigation (Tasks / Weekly Summary),
                                30-second polling, top-level task state
  App.css                     – layout, header, tab bar, shared .btn styles
  types/index.ts              – shared TypeScript interfaces (Task, TaskStatus, …)
  api/client.ts               – typed fetch wrapper — all backend calls go here
  hooks/useTimer.ts           – live-ticking timer hook + formatDuration utility
  components/
    CreateTask.tsx / .css     – collapsible form to add a task
    TaskItem.tsx  / .css      – single task card (inline edit, timer controls)
    TaskList.tsx              – sorts and renders TaskItem list + CreateTask
    WeeklySummary.tsx / .css  – week navigation + bar chart of time per task
```

## Key concepts

### API client (`api/client.ts`)

All backend calls go through the `api` object. Components never call `fetch` directly.

| Method | Backend endpoint |
|--------|-----------------|
| `api.getTasks()` | `GET /api/tasks` |
| `api.createTask(data)` | `POST /api/tasks` |
| `api.updateTask(id, data)` | `PUT /api/tasks/{id}` |
| `api.deleteTask(id)` | `DELETE /api/tasks/{id}` |
| `api.startTimer(id)` | `POST /api/tasks/{id}/start` |
| `api.stopTimer(id)` | `POST /api/tasks/{id}/stop` |
| `api.getWeeklySummary(weekStart?)` | `GET /api/summary/weekly` |

Non-2xx responses throw an `Error` whose message comes from the backend's `message` field.

### State management (`App.tsx`)

- `tasks: Task[]` is the single source of truth for the task list.
- After every mutation (create / update / delete / start timer / stop timer) the response is merged in-place — no full list re-fetch.
- A `setInterval` polls `api.getTasks()` every **30 seconds** to sync with other tabs or sessions.

### Live timer (`hooks/useTimer.ts`)

`useTimer(completedSeconds, isRunning, timerStartedAt): number`

When `isRunning` is `true`, a `setInterval` ticks every second using `timerStartedAt` as the local reference point:

```
display = completedSeconds + floor((Date.now() - new Date(timerStartedAt)) / 1000)
```

`formatDuration(seconds): string` formats the result as `HH:MM:SS`.

### Task sorting (`TaskList.tsx`)

Tasks are sorted before rendering:
1. By status: `IN_PROGRESS` → `TODO` → `DONE`
2. Within the same status: running timers first, then by `createdAt` descending.

### Weekly summary (`WeeklySummary.tsx`)

- Week starts on **Monday** (ISO week).
- The week start is sent as `?weekStart=YYYY-MM-DDTHH:MM:SS` (no timezone suffix) — the backend uses `LocalDateTime.parse()`.
- Bar widths scale relative to the task with the most seconds in the displayed week.
- The **Next** button is disabled when the displayed week is the current week.

## Available scripts

| Script | Description |
|--------|-------------|
| `npm start` | Starts the dev server on port 3000 with hot reload |
| `npm run build` | Creates an optimized production build in `build/` |
| `npm test` | Runs the test suite in watch mode |

## Common issues

**Blank page / network errors on startup**
The backend is not running or not reachable on port 8080. Start the Spring Boot backend first, then refresh the browser.

**`npm start` fails with Node version error**
Upgrade Node.js to v16 or later. `react-scripts 5` does not support Node 14 in all environments.

**Port 3000 already in use**
CRA will prompt you to use a different port. Alternatively, kill the other process or set `PORT=3001 npm start`.

**Changes to `proxy` in `package.json` have no effect**
The proxy setting is only read at dev-server startup. Stop and restart `npm start` after changing it.
