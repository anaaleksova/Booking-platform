import { expect, test } from '@playwright/test';
import { signIn, watchForProblems } from './support';

/** The main screens render without errors, on a desktop and on a phone. */

test('public pages', async ({ page }) => {
  const problems = watchForProblems(page);
  for (const slug of ['serenity-studio', 'harbor-bistro']) {
    await page.goto(`/b/${slug}`);
    await expect(page.getByRole('link', { name: /book/i }).first()).toBeVisible();
    await page.goto(`/b/${slug}/book`);
    await expect(page.getByRole('heading', { name: 'Choose a service' })).toBeVisible();
  }
  await page.goto('/no-such-page');
  await expect(page.getByText(/not found|doesn.t exist/i).first()).toBeVisible();
  expect(problems).toEqual([]);
});

test('sign in and the dashboard', async ({ page }) => {
  const problems = watchForProblems(page);
  await signIn(page);
  await expect(page).toHaveURL(/\/admin$/);
  await page.goto('/admin/reservations');
  await expect(page.getByRole('heading', { name: /reservations/i }).first()).toBeVisible();
  expect(problems).toEqual([]);
});
