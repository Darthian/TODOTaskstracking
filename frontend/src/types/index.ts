export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';

export interface Task {
  id: number;
  title: string;
  description: string | null;
  status: TaskStatus;
  createdAt: string;
  /** Seconds from all completed (stopped) sessions */
  completedSeconds: number;
  /** Total seconds including any currently running session (server-computed) */
  totalSeconds: number;
  isTimerRunning: boolean;
  /** ISO string of when the current timer session started, or null */
  timerStartedAt: string | null;
}

export interface CreateTaskRequest {
  title: string;
  description?: string;
}

export interface UpdateTaskRequest {
  title?: string;
  description?: string;
  status?: TaskStatus;
}

export interface WeeklySummaryEntry {
  taskId: number;
  taskTitle: string;
  taskStatus: TaskStatus;
  totalSeconds: number;
}

export interface WeeklySummary {
  weekStart: string;
  weekEnd: string;
  entries: WeeklySummaryEntry[];
  grandTotalSeconds: number;
}
