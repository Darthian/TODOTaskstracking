import React from 'react';
import { Task, CreateTaskRequest, UpdateTaskRequest, TaskStatus } from '../types';
import { TaskItem } from './TaskItem';
import { CreateTask } from './CreateTask';

interface Props {
  tasks: Task[];
  loading: boolean;
  error: string | null;
  onCreate: (data: CreateTaskRequest) => Promise<void>;
  onStart: (id: number) => Promise<void>;
  onStop: (id: number) => Promise<void>;
  onUpdate: (id: number, data: UpdateTaskRequest) => Promise<void>;
  onDelete: (id: number) => Promise<void>;
}

const STATUS_ORDER: TaskStatus[] = ['IN_PROGRESS', 'TODO', 'DONE'];

export function TaskList({
  tasks,
  loading,
  error,
  onCreate,
  onStart,
  onStop,
  onUpdate,
  onDelete,
}: Props) {
  const sorted = [...tasks].sort((a, b) => {
    const statusDiff = STATUS_ORDER.indexOf(a.status) - STATUS_ORDER.indexOf(b.status);
    if (statusDiff !== 0) return statusDiff;
    // Running timers first within the same status
    if (a.isTimerRunning !== b.isTimerRunning) return a.isTimerRunning ? -1 : 1;
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  });

  return (
    <div className="task-list">
      <CreateTask onCreate={onCreate} />

      {loading && <p className="task-list__state">Loading tasks…</p>}
      {error && <p className="task-list__state task-list__state--error">{error}</p>}

      {!loading && !error && sorted.length === 0 && (
        <p className="task-list__state">No tasks yet. Create one above!</p>
      )}

      {sorted.map((task) => (
        <TaskItem
          key={task.id}
          task={task}
          onStart={onStart}
          onStop={onStop}
          onUpdate={onUpdate}
          onDelete={onDelete}
        />
      ))}
    </div>
  );
}
