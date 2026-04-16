import { test, expect, Page } from '@playwright/test';

// ─── Shared helpers ───────────────────────────────────────────────────────────

/**
 * Delete every task via the API so each test starts with an empty board.
 * Works through the nginx proxy both in Docker (http://frontend) and locally
 * (http://localhost:3000 → CRA proxy → backend).
 */
async function clearAllTasks(page: Page): Promise<void> {
  const res = await page.request.get('/api/tasks');
  const tasks: Array<{ id: number }> = await res.json();
  await Promise.all(tasks.map((t) => page.request.delete(`/api/tasks/${t.id}`)));
}

/**
 * Click the trigger button, fill in the title (and optional description),
 * submit, then wait until the new task title is visible.
 */
async function createTask(
  page: Page,
  title: string,
  description?: string
): Promise<void> {
  await page.click('button.create-task-trigger');
  await page.waitForSelector('input.create-task-form__input', { state: 'visible' });
  await page.fill('input.create-task-form__input', title);
  if (description) {
    await page.fill('textarea.create-task-form__textarea', description);
  }
  await page.click('button[type="submit"]');
  await expect(page.locator('h3.task-item__title').filter({ hasText: title })).toBeVisible();
}

// ─── Tests ────────────────────────────────────────────────────────────────────

test.describe('Task lifecycle', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    await clearAllTasks(page);
    // Reload so the UI reflects the cleared state
    await page.reload();
    await page.waitForLoadState('networkidle');
  });

  // ── CRUD ──────────────────────────────────────────────────────────────────

  test('empty state is shown when no tasks exist', async ({ page }) => {
    await expect(
      page.locator('.task-list__state').filter({ hasText: 'No tasks yet' })
    ).toBeVisible();
  });

  test('creates a task and it appears in the list with TODO status', async ({ page }) => {
    await createTask(page, 'My first task');

    await expect(page.locator('h3.task-item__title').filter({ hasText: 'My first task' })).toBeVisible();
    await expect(page.locator('.status-badge--todo').first()).toBeVisible();
  });

  test('creates a task with description — description visible below title', async ({ page }) => {
    await createTask(page, 'Task with description', 'Detailed notes go here');

    await expect(
      page.locator('.task-item__description').filter({ hasText: 'Detailed notes go here' })
    ).toBeVisible();
  });

  test('cancel discards the create form without saving', async ({ page }) => {
    await page.click('button.create-task-trigger');
    await page.fill('input.create-task-form__input', 'Draft task');
    await page.click('button:has-text("Cancel")');

    // Form collapses
    await expect(page.locator('input.create-task-form__input')).not.toBeVisible();
    // Task was not created
    await expect(
      page.locator('h3.task-item__title').filter({ hasText: 'Draft task' })
    ).not.toBeVisible();
  });

  test('edits a task — title and status update correctly', async ({ page }) => {
    await createTask(page, 'Task to edit');

    const taskItem = page.locator('.task-item').filter({ hasText: 'Task to edit' });

    // Click the Edit button (first ghost small button in that task item's footer)
    await taskItem.locator('.task-item__footer button.btn--ghost').click();

    const titleInput = taskItem.locator('input.edit-input');
    await titleInput.clear();
    await titleInput.fill('Edited title');
    await taskItem.locator('select.edit-select').selectOption('IN_PROGRESS');
    await taskItem.locator('button.btn--primary.btn--sm').click();

    await expect(
      page.locator('h3.task-item__title').filter({ hasText: 'Edited title' })
    ).toBeVisible();
    await expect(
      page.locator('.task-item').filter({ hasText: 'Edited title' }).locator('.status-badge--in_progress')
    ).toBeVisible();
  });

  test('deletes a task — task disappears from the list', async ({ page }) => {
    await createTask(page, 'Task to delete');

    // Accept the window.confirm dialog
    page.once('dialog', (dialog) => dialog.accept());

    await page
      .locator('.task-item')
      .filter({ hasText: 'Task to delete' })
      .locator('button.btn--danger')
      .click();

    await expect(
      page.locator('h3.task-item__title').filter({ hasText: 'Task to delete' })
    ).not.toBeVisible({ timeout: 8_000 });
  });

  // ── Tab navigation ────────────────────────────────────────────────────────

  test('switches to Weekly Summary tab and back', async ({ page }) => {
    await page.click('button.tab-btn:has-text("Weekly Summary")');
    await expect(page.locator('.weekly-summary')).toBeVisible();

    await page.click('button.tab-btn:has-text("Tasks")');
    await expect(page.locator('.task-list')).toBeVisible();
  });
});
