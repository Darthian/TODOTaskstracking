import { useState, useEffect, useRef } from 'react';

/**
 * Returns live elapsed seconds for a task.
 *
 * @param completedSeconds  - seconds from all finished sessions
 * @param isRunning         - whether a timer is currently active
 * @param timerStartedAt    - ISO string of when the current session started
 *
 * The hook ticks every second only when a timer is running.
 * When stopped, it returns the stable completedSeconds value.
 */
export function useTimer(
  completedSeconds: number,
  isRunning: boolean,
  timerStartedAt: string | null
): number {
  const computeTotal = () => {
    if (!isRunning || !timerStartedAt) return completedSeconds;
    const elapsed = Math.floor(
      (Date.now() - new Date(timerStartedAt).getTime()) / 1000
    );
    return completedSeconds + Math.max(0, elapsed);
  };

  const [seconds, setSeconds] = useState(computeTotal);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Recompute when props change (e.g. after a stop or a remote poll)
  useEffect(() => {
    setSeconds(computeTotal());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [completedSeconds, isRunning, timerStartedAt]);

  // Tick every second while running
  useEffect(() => {
    if (!isRunning || !timerStartedAt) return;

    intervalRef.current = setInterval(() => {
      setSeconds(computeTotal());
    }, 1000);

    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isRunning, timerStartedAt, completedSeconds]);

  return seconds;
}

/** Formats a total number of seconds into HH:MM:SS */
export function formatDuration(totalSeconds: number): string {
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(h)}:${pad(m)}:${pad(s)}`;
}
