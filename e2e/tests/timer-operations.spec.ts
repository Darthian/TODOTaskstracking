import { test, expect, Page } from '@playwright/test';

// ─── Helpers ──────────────────────────────────────────────────────────────────

async function clearAllTasks(page: Page): Promise<void> {
  const res = await page.request.get('/api/tasks');
  const tasks: Array<{ id: number }> = await res.json();
  await Promise.all(tasks.map((t) => page.request.delete(`/api/tasks/${t.id}`)));
}

async function createTask(page: Page, title: string): Promise<void> {
  await page.click('button.create-task-trigger');
  await page.waitForSelector('input.create-task-form__input', { state: 'visible' });
  await page.fill('input.create-task-form__input', title);
  await page.click('button[type="submit"]');
  await expect(page.locator('h3.task-item__title').filter({ hasText: title })).toBeVisible();
}

/** Start the timer for a task and wait for the Stop button to appear. */
async function startTimer(page: Page, taskTitle: string): Promise<void> {
  const item = page.locator('.task-item').filter({ hasText: taskTitle });
  await item.locator('button.btn--start').click();
  await expect(item.locator('button.btn--stop')).toBeVisible({ timeout: 8_000 });
}

/** Stop the timer for a task and wait for the Start button to reappear. */
async function stopTimer(page: Page, taskTitle: string): Promise<void> {
  const item = page.locator('.task-item').filter({ hasText: taskTitle });
  await item.locator('button.btn--stop').click();
  await expect(item.locator('button.btn--start')).toBeVisible({ timeout: 8_000 });
}

// ─── Tests ────────────────────────────────────────────────────────────────────

test.describe('Timer operations', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    await clearAllTasks(page);
    await page.reload();
    await page.waitForLoadState('networkidle');
  });

  // ── Start / stop state ────────────────────────────────────────────────────

  test('starts a timer — button switches to Stop and active indicator appears', async ({ page }) => {
    await createTask(page, 'Timer start test');
    await startTimer(page, 'Timer start test');

    const item = page.locator('.task-item').filter({ hasText: 'Timer start test' });
    await expect(item.locator('.timer-display--active')).toBeVisible();
    await expect(page.locator('.running-badge')).toBeVisible();
  });

  test('stops a timer — button reverts to Start and active indicator disappears', async ({ page }) => {
    await createTask(page, 'Timer stop test');
    await startTimer(page, 'Timer stop test');

    // Wait ≥1 s so at least one second accumulates on the server
    await page.waitForTimeout(1_200);

    await stopTimer(page, 'Timer stop test');

    const item = page.locator('.task-item').filter({ hasText: 'Timer stop test' });
    await expect(item.locator('.timer-display--active')).not.toBeVisible();
    await expect(page.locator('.running-badge')).not.toBeVisible();

    // Timer display must show non-zero accumulated time
    const timerText = await item.locator('.timer-display').textContent();
    expect(timerText?.trim()).not.toBe('00:00:00');
  });

  // ── Pause / resume accumulation ───────────────────────────────────────────

  test('second session adds to the first — accumulated time grows monotonically', async ({ page }) => {
    await createTask(page, 'Multi-session task');

    // Session 1
    await startTimer(page, 'Multi-session task');
    await page.waitForTimeout(1_200);
    await stopTimer(page, 'Multi-session task');

    const item = page.locator('.task-item').filter({ hasText: 'Multi-session task' });
    const afterSession1 = await item.locator('.timer-display').textContent();

    // Session 2
    await startTimer(page, 'Multi-session task');
    await page.waitForTimeout(1_200);
    await stopTimer(page, 'Multi-session task');

    const afterSession2 = await item.locator('.timer-display').textContent();

    // Both non-zero and session 2 ≥ session 1
    expect(afterSession1?.trim()).not.toBe('00:00:00');
    expect(afterSession2?.trim()).not.toBe('00:00:00');
    // Lexicographic comparison works for HH:MM:SS strings of equal length
    expect(afterSession2! >= afterSession1!).toBeTruthy();
  });

  // ── Header running badge ──────────────────────────────────────────────────

  test('running badge counts active timers across multiple tasks', async ({ page }) => {
    await createTask(page, 'Badge task A');
    await createTask(page, 'Badge task B');

    // No badge before any timer starts
    await expect(page.locator('.running-badge')).not.toBeVisible();

    await startTimer(page, 'Badge task A');
    await expect(page.locator('.running-badge').filter({ hasText: '1 timer running' })).toBeVisible();

    await startTimer(page, 'Badge task B');
    await expect(page.locator('.running-badge').filter({ hasText: '2 timers running' })).toBeVisible();

    await stopTimer(page, 'Badge task A');
    await expect(page.locator('.running-badge').filter({ hasText: '1 timer running' })).toBeVisible();
  });

  // ── Delete task with running timer ────────────────────────────────────────

  test('deleting a task with a running timer succeeds without an error', async ({ page }) => {
    await createTask(page, 'Task to delete with timer');
    await startTimer(page, 'Task to delete with timer');

    page.once('dialog', (dialog) => dialog.accept());
    await page
      .locator('.task-item')
      .filter({ hasText: 'Task to delete with timer' })
      .locator('button.btn--danger')
      .click();

    await expect(
      page.locator('h3.task-item__title').filter({ hasText: 'Task to delete with timer' })
    ).not.toBeVisible({ timeout: 8_000 });

    // No running badge remains
    await expect(page.locator('.running-badge')).not.toBeVisible();
  });

  // ── Weekly summary ────────────────────────────────────────────────────────

  test('weekly summary shows time tracked this week after a timer session', async ({ page }) => {
    await createTask(page, 'Summarized task');
    await startTimer(page, 'Summarized task');
    await page.waitForTimeout(1_200);
    await stopTimer(page, 'Summarized task');

    // Switch to Weekly Summary tab
    await page.click('button.tab-btn:has-text("Weekly Summary")');
    await page.waitForLoadState('networkidle');

    // The task entry appears in the summary
    await expect(
      page.locator('.summary-entry__title').filter({ hasText: 'Summarized task' })
    ).toBeVisible({ timeout: 8_000 });

    // Grand total is non-zero
    const totalText = await page.locator('.weekly-summary__total').textContent();
    expect(totalText).not.toContain('00:00:00');
  });
});
