import { expect, test } from '@playwright/test';
import { MAILPIT, emailedCode, signIn, uniqueEmail, watchForProblems } from './support';

test('a new customer books a facial with the therapist they chose', async ({ page, request, browser }) => {
  const problems = watchForProblems(page);
  const email = uniqueEmail('guest');

  // Service, therapist, time: no account needed yet.
  await page.goto('/b/serenity-studio/book');
  await page.getByRole('button', { name: /Facial Treatment/ }).click();
  await page.getByRole('button', { name: /^E Elena/ }).click();
  await page.getByRole('button', { name: 'Continue' }).click();
  const time = page.getByRole('button', { name: /^\d\d:\d\d$/ }).last();
  const chosenTime = await time.innerText();
  await time.click();

  // Booking needs an account, proven by the emailed code.
  await page.getByLabel('Full name').fill('Mila Guest');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill('password123');
  await page.getByRole('button', { name: 'Send me a code' }).click();
  await page.getByLabel('Verification code').fill(await emailedCode(request, email));
  await page.getByRole('button', { name: /verify/i }).click();

  // The details come from the account; only the phone is missing.
  await expect(page.getByRole('textbox', { name: 'Email address' })).toHaveValue(email);
  // A number of its own: a number another customer gave must not decide whose booking this is.
  await page.getByRole('textbox', { name: 'Phone number' }).fill(`070 ${100 + Math.floor(Math.random() * 900)} ${100 + Math.floor(Math.random() * 900)}`);
  await page.getByRole('button', { name: 'Continue' }).click();

  await expect(page.getByRole('heading', { name: 'Review & confirm' })).toBeVisible();
  await expect(page.getByText('1 person · Elena').first()).toBeVisible();
  await page.getByRole('button', { name: 'Confirm reservation' }).click();

  // The reservation page.
  await expect(page).toHaveURL(/\/booking\/[0-9a-f-]{36}$/);
  await expect(page.getByText('Facial Treatment').first()).toBeVisible();
  await expect(page.getByText(chosenTime).first()).toBeVisible();
  const reference = page.url().split('/').pop()!;

  // The confirmation reaches this customer (the email worker runs every 30 seconds).
  await expect
    .poll(
      async () => {
        const search = await request.get(`${MAILPIT}/api/v1/search`, { params: { query: `to:"${email}" subject:confirmed` } });
        return (await search.json()).messages_count;
      },
      { timeout: 45_000 },
    )
    .toBe(1);

  // The owner sees it, with the therapist assigned.
  const owner = await browser.newContext();
  const ownerPage = await owner.newPage();
  await signIn(ownerPage, 'owner@serenity.studio', 'password123');
  await expect(ownerPage).toHaveURL(/\/admin$/);
  const reservation = await ownerPage.request.get('/api/admin/reservations?from=2020-01-01&to=2099-12-31');
  const mine = ((await reservation.json()) as { reference: string; resources: { name: string }[] }[]).find(
    (candidate) => candidate.reference === reference,
  );
  expect(mine?.resources.map((resource) => resource.name)).toContain('Elena');
  await owner.close();

  expect(problems).toEqual([]);
});

test('a group of four picks tables on the floor plan', async ({ page }) => {
  const problems = watchForProblems(page);
  await page.goto('/b/harbor-bistro/book');
  await page.getByRole('button', { name: /Dinner/ }).first().click();
  for (let i = 0; i < 3; i++) {
    await page.getByRole('button', { name: 'More people' }).click();
  }
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.getByRole('button', { name: /^\d\d:\d\d$/ }).first().click();
  await expect(page.getByRole('heading', { name: 'Choose your table' })).toBeVisible();

  const map = page.getByRole('group', { name: 'Map of tables' });
  // A 2-seat table cannot seat four alone: clicking it picks the combination it belongs to.
  const table1 = map.getByRole('button', { name: /^Table 1, 2 seats/ });
  await table1.click();
  await expect(table1).toHaveAttribute('aria-pressed', 'true');
  await expect(map.getByRole('button', { name: /^Table 2, 2 seats/ })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('button', { name: /^Table 2 \+ Table 1/ })).toHaveAttribute('aria-pressed', 'true');

  // A table big enough on its own replaces the combination.
  await map.getByRole('button', { name: /^Table 3, 4 seats/ }).click();
  await expect(map.getByRole('button', { name: /^Table 3, 4 seats/ })).toHaveAttribute('aria-pressed', 'true');
  await expect(table1).toHaveAttribute('aria-pressed', 'false');
  await expect(page.getByRole('button', { name: /No preference/ })).toHaveAttribute('aria-pressed', 'false');

  // The same choice in the list.
  await page.getByRole('button', { name: 'List view' }).click();
  await expect(page.getByRole('button', { name: /^T Table 3/ })).toHaveAttribute('aria-pressed', 'true');

  await page.getByRole('button', { name: 'Continue' }).click();
  await expect(page.getByRole('heading', { name: 'Your details' })).toBeVisible();
  expect(page.url()).toContain('pick=');
  expect(problems).toEqual([]);
});

test('the business page and its map load under the Content-Security-Policy', async ({ page }) => {
  const problems = watchForProblems(page);
  await page.goto('/b/harbor-bistro');
  await expect(page.getByRole('heading', { name: 'Harbor Bistro' }).first()).toBeVisible();
  await page.mouse.wheel(0, 4000);
  // The Google Maps embed is the one frame the policy allows.
  const map = page.locator('iframe[title^="Map of"]');
  if (await map.count()) {
    await expect(map).toHaveAttribute('src', /^https:\/\/www\.google\.com\/maps/);
  }
  await page.waitForTimeout(1500);
  expect(problems).toEqual([]);
});
