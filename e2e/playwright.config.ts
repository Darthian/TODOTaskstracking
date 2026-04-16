import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for the TODO tracker E2E suite.
 *
 * Environment variables:
 *   BASE_URL  – override the target URL (default: http://localhost:3000)
 *               In Docker CI it is set to http://frontend by docker-compose.test.yml.
 */
export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  expect: { timeout: 8_000 },

  // Retry on CI to absorb transient failures; no retries locally.
  retries: process.env.CI ? 2 : 0,

  // Serial execution: tests share a single H2 in-memory database; each test
  // cleans up via the API in beforeEach, so parallelism would cause races.
  workers: 1,

  reporter: process.env.CI ? 'github' : 'html',

  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:3000',
    headless: true,
    screenshot: 'only-on-failure',
    video: 'on-first-retry',
    // Give the app extra time to load on first paint after Docker startup
    navigationTimeout: 20_000,
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
