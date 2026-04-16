import { renderHook, act } from '@testing-library/react';
import { useTimer, formatDuration } from './useTimer';

// ---------------------------------------------------------------------------
// formatDuration
// ---------------------------------------------------------------------------

describe('formatDuration', () => {
  test.each([
    [0,     '00:00:00'],
    [1,     '00:00:01'],
    [59,    '00:00:59'],
    [60,    '00:01:00'],
    [90,    '00:01:30'],
    [3600,  '01:00:00'],
    [3661,  '01:01:01'],
    [86399, '23:59:59'],
    [86400, '24:00:00'],
  ])('formatDuration(%i) === %s', (input, expected) => {
    expect(formatDuration(input)).toBe(expected);
  });
});

// ---------------------------------------------------------------------------
// useTimer
// ---------------------------------------------------------------------------

describe('useTimer', () => {
  test('stopped timer (isRunning=false, timerStartedAt=null) returns completedSeconds', () => {
    const { result } = renderHook(() => useTimer(300, false, null));
    expect(result.current).toBe(300);
  });

  test('running but no timerStartedAt returns completedSeconds', () => {
    const { result } = renderHook(() => useTimer(150, true, null));
    expect(result.current).toBe(150);
  });

  test('running 60 s ago returns value in [completedSeconds+60, completedSeconds+65)', () => {
    const timerStartedAt = new Date(Date.now() - 60_000).toISOString();
    const { result } = renderHook(() => useTimer(100, true, timerStartedAt));
    expect(result.current).toBeGreaterThanOrEqual(160);
    expect(result.current).toBeLessThan(165);
  });

  test('future timerStartedAt (clock skew) never produces a negative value', () => {
    const timerStartedAt = new Date(Date.now() + 10_000).toISOString();
    const { result } = renderHook(() => useTimer(0, true, timerStartedAt));
    expect(result.current).toBeGreaterThanOrEqual(0);
  });

  test('rerender to stopped state returns new completedSeconds', () => {
    const startedAt = new Date(Date.now() - 5_000).toISOString();

    const { result, rerender } = renderHook(
      ({ completed, running, startedAt }: {
        completed: number;
        running: boolean;
        startedAt: string | null;
      }) => useTimer(completed, running, startedAt),
      { initialProps: { completed: 60, running: true, startedAt } }
    );

    // While running the value should be at least 60 + 5 seconds elapsed.
    expect(result.current).toBeGreaterThanOrEqual(65);

    act(() => {
      rerender({ completed: 120, running: false, startedAt: null });
    });

    expect(result.current).toBe(120);
  });
});
