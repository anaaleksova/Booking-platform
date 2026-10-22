import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests against a running stack (the app, its API and Mailpit),
 * by default the Docker setup: `docker compose --profile full up --build`.
 *
 *   E2E_BASE_URL     the app            (default http://localhost:3000)
 *   E2E_MAILPIT_URL  Mailpit's web API  (default http://localhost:8025)
 *
 * The backend must run with the dev profile, which loads the demo businesses.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'phone', use: { ...devices['Pixel 7'] }, testMatch: /smoke\.spec\.ts/ },
  ],
});
