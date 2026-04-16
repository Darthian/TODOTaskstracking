import {
  Task,
  CreateTaskRequest,
  UpdateTaskRequest,
  WeeklySummary,
} from '../types';

const BASE_URL = '/api';

async function request<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  });

  if (!res.ok) {
    let message = `Request failed: ${res.status} ${res.statusText}`;
    try {
      const err = await res.json();
      message = err.message ?? message;
    } catch {
      // ignore JSON parse error
    }
    throw new Error(message);
  }

  // 204 No Content
  if (res.status === 204) return undefined as unknown as T;
  return res.json();
}

// ─── Tasks ────────────────────────────────────────────────────────────────────

export const api = {
  getTasks: (): Promise<Task[]> => request('/tasks'),

  getTask: (id: number): Promise<Task> => request(`/tasks/${id}`),

  createTask: (data: CreateTaskRequest): Promise<Task> =>
    request('/tasks', { method: 'POST', body: JSON.stringify(data) }),

  updateTask: (id: number, data: UpdateTaskRequest): Promise<Task> =>
    request(`/tasks/${id}`, { method: 'PUT', body: JSON.stringify(data) }),

  deleteTask: (id: number): Promise<void> =>
    request(`/tasks/${id}`, { method: 'DELETE' }),

  startTimer: (id: number): Promise<Task> =>
    request(`/tasks/${id}/start`, { method: 'POST' }),

  stopTimer: (id: number): Promise<Task> =>
    request(`/tasks/${id}/stop`, { method: 'POST' }),

  // ─── Summary ────────────────────────────────────────────────────────────────

  getWeeklySummary: (weekStart?: string): Promise<WeeklySummary> => {
    const qs = weekStart ? `?weekStart=${encodeURIComponent(weekStart)}` : '';
    return request(`/summary/weekly${qs}`);
  },
};
