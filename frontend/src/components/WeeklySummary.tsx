import React, { useState, useEffect, useCallback } from 'react';
import { WeeklySummary as WeeklySummaryType } from '../types';
import { api } from '../api/client';
import { formatDuration } from '../hooks/useTimer';
import './WeeklySummary.css';

function startOfWeek(date: Date): Date {
  const d = new Date(date);
  const day = d.getDay(); // 0 = Sunday
  const diff = day === 0 ? -6 : 1 - day; // shift to Monday
  d.setDate(d.getDate() + diff);
  d.setHours(0, 0, 0, 0);
  return d;
}

function toISOLocalDateTime(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T00:00:00`;
}

function formatWeekLabel(isoStr: string): string {
  const d = new Date(isoStr);
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

export function WeeklySummary() {
  const [weekStart, setWeekStart] = useState<Date>(() => startOfWeek(new Date()));
  const [summary, setSummary] = useState<WeeklySummaryType | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (start: Date) => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getWeeklySummary(toISOLocalDateTime(start));
      setSummary(data);
    } catch (err: any) {
      setError(err.message ?? 'Failed to load summary');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(weekStart);
  }, [weekStart, load]);

  const prevWeek = () => {
    setWeekStart((d) => {
      const next = new Date(d);
      next.setDate(next.getDate() - 7);
      return next;
    });
  };

  const nextWeek = () => {
    setWeekStart((d) => {
      const next = new Date(d);
      next.setDate(next.getDate() + 7);
      return next;
    });
  };

  const isCurrentWeek = startOfWeek(new Date()).getTime() === weekStart.getTime();

  const maxSeconds = summary
    ? Math.max(...summary.entries.map((e) => e.totalSeconds), 1)
    : 1;

  return (
    <div className="weekly-summary">
      {/* Week navigation */}
      <div className="weekly-summary__nav">
        <button className="nav-btn" onClick={prevWeek} title="Previous week">
          &#8592;
        </button>
        <div className="weekly-summary__week-label">
          <span className="week-range">
            {summary
              ? `${formatWeekLabel(summary.weekStart)} – ${formatWeekLabel(summary.weekEnd)}`
              : '…'}
          </span>
          {isCurrentWeek && <span className="current-week-badge">This week</span>}
        </div>
        <button
          className="nav-btn"
          onClick={nextWeek}
          disabled={isCurrentWeek}
          title="Next week"
        >
          &#8594;
        </button>
      </div>

      {/* Grand total */}
      {summary && (
        <div className="weekly-summary__total">
          Total tracked:{' '}
          <strong>{formatDuration(summary.grandTotalSeconds)}</strong>
        </div>
      )}

      {/* Content */}
      {loading && <p className="weekly-summary__state">Loading…</p>}
      {error && <p className="weekly-summary__state weekly-summary__state--error">{error}</p>}

      {!loading && !error && summary && summary.entries.length === 0 && (
        <p className="weekly-summary__state">No tracked time this week.</p>
      )}

      {!loading && !error && summary && summary.entries.length > 0 && (
        <div className="summary-entries">
          {summary.entries.map((entry) => {
            const pct = Math.round((entry.totalSeconds / maxSeconds) * 100);
            return (
              <div key={entry.taskId} className="summary-entry">
                <div className="summary-entry__meta">
                  <span className="summary-entry__title">{entry.taskTitle}</span>
                  <span className="summary-entry__time">
                    {formatDuration(entry.totalSeconds)}
                  </span>
                </div>
                <div className="summary-entry__bar-track">
                  <div
                    className={`summary-entry__bar summary-entry__bar--${entry.taskStatus.toLowerCase()}`}
                    style={{ width: `${pct}%` }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
