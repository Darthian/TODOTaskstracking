import React, { useState, useEffect, useCallback } from 'react';
import { Task, CreateTaskRequest, UpdateTaskRequest } from './types';
import { api } from './api/client';
import { TaskList } from './components/TaskList';
import { WeeklySummary } from './components/WeeklySummary';
import './App.css';

type Tab = 'tasks' | 'summary';

export default function App() {
  const [tab, setTab] = useState<Tab>('tasks');
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // ─── Polling ─────────────────────────────────────────────────────────────
  // Refresh task list every 30 seconds to sync state from other sessions,
  // handle server-side timer drift, etc.
  const refreshTasks = useCallback(async () => {
    try {
      const data = await api.getTasks();
      setTasks(data);
      setError(null);
    } catch (err: any) {
      setError(err.message ?? 'Failed to load tasks');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshTasks();
    const id = setInterval(refreshTasks, 30_000);
    return () => clearInterval(id);
  }, [refreshTasks]);

  // ─── Task operations ──────────────────────────────────────────────────────

  const handleCreate = async (data: CreateTaskRequest) => {
    const created = await api.createTask(data);
    setTasks((prev) => [created, ...prev]);
  };

  const handleUpdate = async (id: number, data: UpdateTaskRequest) => {
    const updated = await api.updateTask(id, data);
    setTasks((prev) => prev.map((t) => (t.id === id ? updated : t)));
  };

  const handleDelete = async (id: number) => {
    await api.deleteTask(id);
    setTasks((prev) => prev.filter((t) => t.id !== id));
  };

  const handleStart = async (id: number) => {
    const updated = await api.startTimer(id);
    setTasks((prev) => prev.map((t) => (t.id === id ? updated : t)));
  };

  const handleStop = async (id: number) => {
    const updated = await api.stopTimer(id);
    setTasks((prev) => prev.map((t) => (t.id === id ? updated : t)));
  };

  // ─── Running timers count ─────────────────────────────────────────────────
  const runningCount = tasks.filter((t) => t.isTimerRunning).length;

  return (
    <div className="app">
      {/* Header */}
      <header className="app-header">
        <div className="app-header__inner">
          <div className="app-header__brand">
            <span className="app-header__icon">⏱</span>
            <h1 className="app-header__title">Task Tracker</h1>
          </div>
          {runningCount > 0 && (
            <span className="running-badge">
              {runningCount} timer{runningCount > 1 ? 's' : ''} running
            </span>
          )}
        </div>
      </header>

      {/* Tab bar */}
      <nav className="tab-bar">
        <div className="tab-bar__inner">
          <button
            className={`tab-btn ${tab === 'tasks' ? 'tab-btn--active' : ''}`}
            onClick={() => setTab('tasks')}
          >
            Tasks
            {tasks.length > 0 && (
              <span className="tab-count">{tasks.length}</span>
            )}
          </button>
          <button
            className={`tab-btn ${tab === 'summary' ? 'tab-btn--active' : ''}`}
            onClick={() => setTab('summary')}
          >
            Weekly Summary
          </button>
        </div>
      </nav>

      {/* Main content */}
      <main className="app-main">
        {tab === 'tasks' ? (
          <TaskList
            tasks={tasks}
            loading={loading}
            error={error}
            onCreate={handleCreate}
            onStart={handleStart}
            onStop={handleStop}
            onUpdate={handleUpdate}
            onDelete={handleDelete}
          />
        ) : (
          <WeeklySummary />
        )}
      </main>
    </div>
  );
}
