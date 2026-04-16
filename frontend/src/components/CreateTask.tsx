import React, { useState } from 'react';
import { CreateTaskRequest } from '../types';
import './CreateTask.css';

interface Props {
  onCreate: (data: CreateTaskRequest) => Promise<void>;
}

export function CreateTask({ onCreate }: Props) {
  const [expanded, setExpanded] = useState(false);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedTitle = title.trim();
    if (!trimmedTitle) return;

    setLoading(true);
    setError(null);
    try {
      await onCreate({ title: trimmedTitle, description: description.trim() || undefined });
      setTitle('');
      setDescription('');
      setExpanded(false);
    } catch (err: any) {
      setError(err.message ?? 'Failed to create task');
    } finally {
      setLoading(false);
    }
  };

  if (!expanded) {
    return (
      <button className="create-task-trigger" onClick={() => setExpanded(true)}>
        <span className="create-task-trigger__icon">+</span>
        Add new task
      </button>
    );
  }

  return (
    <form className="create-task-form" onSubmit={handleSubmit}>
      <input
        className="create-task-form__input"
        type="text"
        placeholder="Task title"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        autoFocus
        maxLength={255}
        required
      />
      <textarea
        className="create-task-form__textarea"
        placeholder="Description (optional)"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        rows={2}
        maxLength={2000}
      />
      {error && <p className="create-task-form__error">{error}</p>}
      <div className="create-task-form__actions">
        <button
          type="button"
          className="btn btn--ghost"
          onClick={() => {
            setExpanded(false);
            setError(null);
          }}
          disabled={loading}
        >
          Cancel
        </button>
        <button
          type="submit"
          className="btn btn--primary"
          disabled={loading || !title.trim()}
        >
          {loading ? 'Creating…' : 'Create Task'}
        </button>
      </div>
    </form>
  );
}
