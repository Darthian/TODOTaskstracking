import React, { useState } from 'react';
import { Task, TaskStatus, UpdateTaskRequest } from '../types';
import { useTimer, formatDuration } from '../hooks/useTimer';
import './TaskItem.css';

interface Props {
  task: Task;
  onStart: (id: number) => Promise<void>;
  onStop: (id: number) => Promise<void>;
  onUpdate: (id: number, data: UpdateTaskRequest) => Promise<void>;
  onDelete: (id: number) => Promise<void>;
}

const STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: 'To Do',
  IN_PROGRESS: 'In Progress',
  DONE: 'Done',
};

export function TaskItem({ task, onStart, onStop, onUpdate, onDelete }: Props) {
  const [timerLoading, setTimerLoading] = useState(false);
  const [editing, setEditing] = useState(false);
  const [editTitle, setEditTitle] = useState(task.title);
  const [editDesc, setEditDesc] = useState(task.description ?? '');
  const [editStatus, setEditStatus] = useState<TaskStatus>(task.status);
  const [editLoading, setEditLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Live-ticking timer display
  const displaySeconds = useTimer(task.completedSeconds, task.isTimerRunning, task.timerStartedAt);

  const handleTimer = async () => {
    setTimerLoading(true);
    setError(null);
    try {
      if (task.isTimerRunning) {
        await onStop(task.id);
      } else {
        await onStart(task.id);
      }
    } catch (err: any) {
      setError(err.message ?? 'Timer operation failed');
    } finally {
      setTimerLoading(false);
    }
  };

  const handleSaveEdit = async () => {
    if (!editTitle.trim()) return;
    setEditLoading(true);
    setError(null);
    try {
      await onUpdate(task.id, {
        title: editTitle.trim(),
        description: editDesc.trim() || undefined,
        status: editStatus,
      });
      setEditing(false);
    } catch (err: any) {
      setError(err.message ?? 'Update failed');
    } finally {
      setEditLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!window.confirm(`Delete "${task.title}"?`)) return;
    try {
      await onDelete(task.id);
    } catch (err: any) {
      setError(err.message ?? 'Delete failed');
    }
  };

  return (
    <div className={`task-item task-item--${task.status.toLowerCase()} ${task.isTimerRunning ? 'task-item--running' : ''}`}>
      {/* Header row */}
      <div className="task-item__header">
        <span className={`status-badge status-badge--${task.status.toLowerCase()}`}>
          {STATUS_LABELS[task.status]}
        </span>

        <div className="task-item__timer">
          <span className={`timer-display ${task.isTimerRunning ? 'timer-display--active' : ''}`}>
            {task.isTimerRunning && <span className="timer-dot" />}
            {formatDuration(displaySeconds)}
          </span>

          <button
            className={`btn btn--timer ${task.isTimerRunning ? 'btn--stop' : 'btn--start'}`}
            onClick={handleTimer}
            disabled={timerLoading}
            title={task.isTimerRunning ? 'Stop timer' : 'Start timer'}
          >
            {timerLoading
              ? '…'
              : task.isTimerRunning
              ? '⏹ Stop'
              : '▶ Start'}
          </button>
        </div>
      </div>

      {/* Body */}
      {editing ? (
        <div className="task-item__edit-form">
          <input
            className="edit-input"
            value={editTitle}
            onChange={(e) => setEditTitle(e.target.value)}
            placeholder="Task title"
            maxLength={255}
          />
          <textarea
            className="edit-textarea"
            value={editDesc}
            onChange={(e) => setEditDesc(e.target.value)}
            placeholder="Description (optional)"
            rows={2}
            maxLength={2000}
          />
          <select
            className="edit-select"
            value={editStatus}
            onChange={(e) => setEditStatus(e.target.value as TaskStatus)}
          >
            <option value="TODO">To Do</option>
            <option value="IN_PROGRESS">In Progress</option>
            <option value="DONE">Done</option>
          </select>
          <div className="task-item__edit-actions">
            <button
              className="btn btn--ghost btn--sm"
              onClick={() => { setEditing(false); setError(null); }}
              disabled={editLoading}
            >
              Cancel
            </button>
            <button
              className="btn btn--primary btn--sm"
              onClick={handleSaveEdit}
              disabled={editLoading || !editTitle.trim()}
            >
              {editLoading ? 'Saving…' : 'Save'}
            </button>
          </div>
        </div>
      ) : (
        <div className="task-item__body">
          <h3 className="task-item__title">{task.title}</h3>
          {task.description && (
            <p className="task-item__description">{task.description}</p>
          )}
          <p className="task-item__created">
            Created {new Date(task.createdAt).toLocaleDateString(undefined, {
              year: 'numeric', month: 'short', day: 'numeric',
            })}
          </p>
        </div>
      )}

      {error && <p className="task-item__error">{error}</p>}

      {/* Footer actions */}
      {!editing && (
        <div className="task-item__footer">
          <button
            className="btn btn--ghost btn--sm"
            onClick={() => {
              setEditTitle(task.title);
              setEditDesc(task.description ?? '');
              setEditStatus(task.status);
              setEditing(true);
              setError(null);
            }}
          >
            Edit
          </button>
          <button
            className="btn btn--danger btn--sm"
            onClick={handleDelete}
          >
            Delete
          </button>
        </div>
      )}
    </div>
  );
}
