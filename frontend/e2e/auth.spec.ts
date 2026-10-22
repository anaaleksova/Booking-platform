import { expect, test } from '@playwright/test';
import { DEMO_OWNER, emailedCode, signIn, uniqueEmail, watchForProblems } from './support';

test.describe('session', () => {
  test('signing in keeps the session in an httpOnly cookie, not in page storage', async ({ page, context }) => {
    const problems = watchForProblems(page);
    await signIn(page);
    await expect(page).toHaveURL(/\/admin$/);

    const session = (await context.cookies()).find((cookie) => cookie.name === 'booking_session');
    expect(session, 'session cookie').toBeDefined();
    expect(session!.httpOnly).toBe(true);
    expect(session!.sameSite).toBe('Lax');
    expect(session!.path).toBe('/api');
    // Nothing a script on the page could read.
    expect(await page.evaluate(() => [localStorage.length, sessionStorage.length, document.cookie])).toEqual([0, 0, '']);

    // A reload keeps the session.
    await page.reload();
    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible();
    expect(problems).toEqual([]);
  });

  test('signing out ends the session on the server too', async ({ page, context }) => {
    await signIn(page);
    await expect(page).toHaveURL(/\/admin$/);

    await page.getByRole('button', { name: /account|harbor|owner/i }).first().click();
    await page.getByRole('menuitem', { name: /sign out|log out/i }).click();
    await expect(page).toHaveURL(/\/login/);

    expect((await context.cookies()).find((cookie) => cookie.name === 'booking_session')).toBeUndefined();
    await page.goto('/admin');
    await expect(page).toHaveURL(/\/login/);
  });

  test('a write without the app header is refused even with a valid session', async ({ page }) => {
    await signIn(page);
    await expect(page).toHaveURL(/\/admin$/);

    // What a forged cross-site request carries: the cookie, but not X-Requested-With.
    const forged = await page.request.put('/api/admin/business/settings', { data: {} });
    expect(forged.status()).toBe(403);
  });
});

test.describe('business sign-up', () => {
  test('creates nothing until the emailed code is entered', async ({ page, request }) => {
    const problems = watchForProblems(page);
    const email = uniqueEmail('owner');

    await page.goto('/register');
    await page.getByLabel('Your name').fill('Ana Tester');
    await page.getByLabel('Business name').fill('E2E Barbers');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password', { exact: true }).fill('password123');
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page.getByText('Check your email')).toBeVisible();
    // Signing in is not possible yet: there is no account.
    const early = await request.post('/api/auth/login', { data: { email, password: 'password123' } });
    expect(early.status()).toBe(401);

    await page.getByLabel('Verification code').fill(await emailedCode(request, email));
    await page.getByRole('button', { name: /verify/i }).click();

    await expect(page).toHaveURL(/\/admin$/);
    await expect(page.getByText('E2E Barbers').first()).toBeVisible();
    expect(problems).toEqual([]);
  });

  test('a wrong code says how many tries are left', async ({ page }) => {
    await page.goto('/register');
    await page.getByLabel('Your name').fill('Ana Tester');
    await page.getByLabel('Business name').fill('E2E Wrong Code');
    await page.getByLabel('Email').fill(uniqueEmail('wrong'));
    await page.getByLabel('Password', { exact: true }).fill('password123');
    await page.getByRole('button', { name: 'Create account' }).click();

    await page.getByLabel('Verification code').fill('000000');
    await page.getByRole('button', { name: /verify/i }).click();
    await expect(page.getByText(/tries left|not right/i)).toBeVisible();
  });
});

test('the demo owner reaches every admin page without errors', async ({ page }) => {
  const problems = watchForProblems(page);
  await signIn(page, DEMO_OWNER.email, DEMO_OWNER.password);
  await expect(page).toHaveURL(/\/admin$/);

  for (const path of [
    'reservations',
    'services',
    'resources',
    'resource-types',
    'employees',
    'customers',
    'discounts',
    'revenue',
    'analytics',
    'integrations',
    'settings',
  ]) {
    await page.goto(`/admin/${path}`);
    await expect(page.locator('main, [role="main"]').first()).toBeVisible();
    await expect(page.getByText(/something went wrong|could not load/i)).toHaveCount(0);
  }
  await page.goto('/admin/integrations');
  await expect(page.getByRole('heading', { name: 'Integrations' })).toBeVisible();
  await expect(page.getByText('API tokens')).toBeVisible();

  // The floor plan editor, which was split up.
  await page.goto('/admin/resource-types');
  await page.getByRole('button', { name: /floor plan/i }).first().click();
  await expect(page.getByRole('dialog')).toContainText('Floor plans');
  await expect(page.getByRole('button', { name: /— drag or use arrow keys/ }).first()).toBeVisible();
  expect(problems).toEqual([]);
});
