import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const backendURL = process.env.AINOTE_BACKEND_URL ?? 'http://127.0.0.1:8081';
const frontendApiBase = process.env.AINOTE_FRONTEND_API_BASE ?? '';
const runFullstack = process.env.AINOTE_E2E_FULLSTACK === 'true';

type AuthPayload = {
  token: string;
  userId: string;
  username: string;
  email: string;
  password: string;
};

type NotePayload = {
  id: string;
  title: string;
  content: string;
};

async function registerUser(request: APIRequestContext): Promise<AuthPayload> {
  const stamp = `${Date.now()}_${Math.floor(Math.random() * 100000)}`;
  const username = `e2e_${stamp}`;
  const email = `${username}@example.test`;
  const password = 'CodexE2E123!';

  const register = await request.post(`${backendURL}/api/auth/register`, {
    data: { username, email, password },
  });

  expect(register.ok()).toBeTruthy();
  const auth = await register.json() as AuthPayload;
  expect(auth.token).toBeTruthy();
  expect(auth.userId).toBeTruthy();
  return { ...auth, password };
}

function authHeaders(auth: AuthPayload) {
  return { Authorization: `Bearer ${auth.token}` };
}

async function authenticatePage(page: Page, auth: AuthPayload) {
  await page.addInitScript((payload: AuthPayload) => {
    localStorage.setItem('token', payload.token);
    localStorage.setItem('auth-storage', JSON.stringify({
      state: {
        token: payload.token,
        user: {
          userId: payload.userId,
          username: payload.username,
          email: payload.email,
        },
        isAuthenticated: true,
      },
      version: 0,
    }));
  }, auth);
}

async function loginThroughUi(page: Page, auth: AuthPayload) {
  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await page.getByLabel('账号').fill(auth.username);
  await page.getByLabel('密码').fill(auth.password);
  await page.getByRole('button', { name: '开始书写' }).click();

  await expect(page.getByRole('listbox', { name: 'Notes' })).toBeVisible({ timeout: 30_000 });
  await expect.poll(() => page.evaluate(() => localStorage.getItem('token'))).toBeTruthy();
}

async function createNote(
  request: APIRequestContext,
  auth: AuthPayload,
  title: string,
  content: string,
): Promise<NotePayload> {
  const response = await request.post(`${backendURL}/api/notes`, {
    headers: authHeaders(auth),
    data: { title, content, tags: ['e2e'], pinned: false, starred: false },
  });
  expect(response.ok()).toBeTruthy();
  return await response.json() as NotePayload;
}

async function listNotes(request: APIRequestContext, auth: AuthPayload): Promise<NotePayload[]> {
  const response = await request.get(`${backendURL}/api/notes`, {
    headers: authHeaders(auth),
  });
  expect(response.ok()).toBeTruthy();
  return await response.json() as NotePayload[];
}

async function listSchedules(request: APIRequestContext, auth: AuthPayload): Promise<unknown[]> {
  const response = await request.get(`${backendURL}/api/schedules`, {
    headers: authHeaders(auth),
  });
  expect(response.ok()).toBeTruthy();
  return await response.json() as unknown[];
}

async function ensureAiAssistantOpen(page: Page) {
  const assistant = page.getByRole('complementary', { name: 'AI chat assistant' });
  if (await assistant.isVisible().catch(() => false)) {
    return;
  }
  await page.getByLabel('Open AI assistant').click();
  await expect(assistant).toBeVisible();
}

test.describe('full-stack E2E', () => {
  test.skip(!runFullstack, 'Set AINOTE_E2E_FULLSTACK=true and start the backend to run full-stack E2E');

  test.beforeEach(async ({ page }) => {
    await page.addInitScript((baseURL) => {
      window.localStorage.setItem('ainote.apiBaseUrl', baseURL as string);
    }, frontendApiBase);
  });

  test('authenticated frontend creates edits autosaves and reloads persisted note data', async ({ page, request }) => {
    const auth = await registerUser(request);
    const noteTitle = `E2E autosave ${Date.now()}`;
    const noteBody = 'Fullstack autosave body persisted through the real backend.';

    await loginThroughUi(page, auth);

    await page.locator('.note-list-create-btn').click();
    await expect(page.locator('.col-span-full')).toContainText('Note created');
    await expect.poll(async () => (await listNotes(request, auth)).length).toBe(1);
    await expect(page.getByRole('option', { name: /Untitled/ })).toHaveCount(1);
    await page.locator('.paper-title-input').fill(noteTitle);
    await page.locator('.tiptap-editor').fill(noteBody);

    await expect.poll(
      async () => {
        const notes = await listNotes(request, auth);
        const saved = notes.find((note) => note.title === noteTitle);
        return saved?.content ?? '';
      },
      { timeout: 30_000 },
    ).toContain(noteBody);

    await page.reload();
    await expect(page.getByRole('listbox', { name: 'Notes' })).toBeVisible();
    await page.getByRole('option', { name: noteTitle }).click();
    await expect(page.locator('.tiptap-editor')).toContainText(noteBody);
  });

  test('selected note streams real backend AI response and exposes clickable source references', async ({ page, request }) => {
    test.setTimeout(180_000);
    const auth = await registerUser(request);
    await createNote(
      request,
      auth,
      'E2E AI source note',
      '<p>Project Aurora launch review is scheduled for quality gates, SSE verification, and release notes.</p>',
    );
    await authenticatePage(page, auth);

    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await page.getByRole('option', { name: /E2E AI source note/ }).click();
    await ensureAiAssistantOpen(page);

    const input = page.locator('textarea').last();
    await input.fill('Summarize this selected note in exactly one short sentence.');
    await input.press('Enter');

    await expect(page.getByText('[1] E2E AI source note')).toBeVisible({ timeout: 180_000 });
    await page.getByText('[1] E2E AI source note').click();
    await expect(page.getByRole('option', { name: /E2E AI source note/ })).toHaveAttribute('aria-selected', 'true');
  });

  test('real backend schedule extraction confirms and persists extracted schedules', async ({ page, request }) => {
    test.setTimeout(180_000);
    const auth = await registerUser(request);
    await createNote(
      request,
      auth,
      'E2E schedule extraction note',
      '<p>Schedule a launch readiness review on July 10, 2026 at 10:00 AM for 30 minutes with the release team.</p>',
    );
    await authenticatePage(page, auth);

    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await page.getByRole('option', { name: /E2E schedule extraction note/ }).click();
    await page.getByLabel('Extract schedule').click();

    await expect.poll(
      async () => page.locator('.extract-item').count(),
      { timeout: 150_000 },
    ).toBeGreaterThan(0);
    await page.locator('.extract-dialog-actions .primary').click();

    await expect.poll(
      async () => (await listSchedules(request, auth)).length,
      { timeout: 30_000 },
    ).toBeGreaterThan(0);
  });

  test('destructive selected-note AI action remains pending until explicit cancellation', async ({ page, request }) => {
    test.setTimeout(180_000);
    const auth = await registerUser(request);
    const note = await createNote(
      request,
      auth,
      'E2E pending delete note',
      '<p>This note must not be deleted unless the pending action is confirmed.</p>',
    );
    await authenticatePage(page, auth);

    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await page.getByRole('option', { name: /E2E pending delete note/ }).click();
    await ensureAiAssistantOpen(page);

    const input = page.locator('textarea').last();
    await input.fill('delete current note');
    await input.press('Enter');

    await expect(page.getByText('PENDING_ACTION · 需要你确认')).toBeVisible({ timeout: 180_000 });
    await expect(page.getByText('不会自动执行')).toBeVisible();
    await page.getByRole('button', { name: '取消' }).click();
    await expect(page.getByText('已取消')).toBeVisible({ timeout: 30_000 });

    const reloaded = await request.get(`${backendURL}/api/notes/${note.id}`, {
      headers: authHeaders(auth),
    });
    expect(reloaded.ok()).toBeTruthy();
  });

  test('real backend 401 clears the authenticated shell and redirects to login', async ({ page }) => {
    await page.goto('/login', { waitUntil: 'domcontentloaded' });
    await page.evaluate(() => {
      localStorage.setItem('token', 'expired.invalid.token');
      localStorage.setItem('auth-storage', JSON.stringify({
        state: {
          token: 'expired.invalid.token',
          user: { userId: 'expired-user', username: 'expired', email: 'expired@example.test' },
          isAuthenticated: true,
        },
        version: 0,
      }));
    });

    await page.goto('/', { waitUntil: 'domcontentloaded' });

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.locator('input[type="password"]')).toBeVisible();
    await expect.poll(() => page.evaluate(() => localStorage.getItem('token'))).toBeNull();
  });
});
