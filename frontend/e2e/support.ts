import { expect, type APIRequestContext, type Page } from '@playwright/test';

export const MAILPIT = process.env.E2E_MAILPIT_URL ?? 'http://localhost:8025';

export const DEMO_OWNER = { email: 'owner@harbor-bistro.com', password: 'password123' };

/** A fresh address per run, so sign-ups never collide with an earlier one. */
export function uniqueEmail(prefix: string) {
  return `${prefix}.${Date.now()}.${Math.floor(Math.random() * 1000)}@e2e.test`;
}

/** The 6-digit code in the newest email to {@code to}, waiting for it to arrive. */
export async function emailedCode(request: APIRequestContext, to: string): Promise<string> {
  let code: string | undefined;
  await expect
    .poll(
      async () => {
        const search = await request.get(`${MAILPIT}/api/v1/search`, { params: { query: `to:"${to}"` } });
        const messages = (await search.json()).messages as { ID: string }[];
        if (!messages?.length) {
          return undefined;
        }
        const message = await (await request.get(`${MAILPIT}/api/v1/message/${messages[0].ID}`)).json();
        code = /\b(\d{6})\b/.exec(message.Text ?? '')?.[1];
        return code;
      },
      { timeout: 20_000, message: `no code emailed to ${to}` },
    )
    .toMatch(/^\d{6}$/);
  return code!;
}

/**
 * Collects what would show a broken page: uncaught errors, failed requests to
 * our API, and anything the Content-Security-Policy blocked.
 */
export function watchForProblems(page: Page) {
  const problems: string[] = [];
  page.on('pageerror', (error) => problems.push(`page error: ${error.message}`));
  page.on('console', (message) => {
    if (message.type() === 'error' && /Content Security Policy|Refused to/i.test(message.text())) {
      problems.push(`CSP: ${message.text()}`);
    }
  });
  page.on('response', (response) => {
    if (response.url().includes('/api/') && response.status() >= 500) {
      problems.push(`${response.status()} ${response.url()}`);
    }
  });
  return problems;
}

export async function signIn(page: Page, email = DEMO_OWNER.email, password = DEMO_OWNER.password) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: /sign in/i }).click();
}
