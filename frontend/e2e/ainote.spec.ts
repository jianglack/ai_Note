import { expect, test, type Page } from '@playwright/test';

const seededNotes = [
  {
    id: 'note-1',
    title: 'Roadmap review',
    content: '<p>Finalize AI note workflow coverage.</p>',
    folderId: null,
    createdAt: '2026-06-27T06:00:00Z',
    updatedAt: '2026-06-27T06:30:00Z',
    tags: [{ id: 'tag-1', name: 'planning' }],
    pinned: true,
    starred: false,
  },
];

const trashNote = {
  ...seededNotes[0],
  id: 'trash-1',
  title: 'Archived meeting note',
  deletedAt: '2026-06-27T07:10:00Z',
  updatedAt: '2026-06-27T07:10:00Z',
};

type MockState = {
  unauthorizedNotes?: boolean;
  actionFeedbackRequests?: unknown[];
  permanentDeleteRequests?: string[];
  createdSchedules?: unknown[];
};

async function mockBackend(page: Page, state: MockState = {}) {
  state.actionFeedbackRequests ??= [];
  state.permanentDeleteRequests ??= [];
  state.createdSchedules ??= [];

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;

    if (path === '/api/auth/login' && request.method() === 'POST') {
      await route.fulfill({
        json: {
          token: 'e2e-token',
          userId: 'user-1',
          username: 'writer',
          email: 'writer@example.test',
        },
      });
      return;
    }

    if (state.unauthorizedNotes && path === '/api/notes' && request.method() === 'GET') {
      await route.fulfill({ status: 401, json: { message: 'token expired' } });
      return;
    }

    if (path === '/api/schedules') {
      if (request.method() === 'POST') {
        const body = request.postDataJSON() as { title?: string; startTime?: string };
        const created = {
          id: `schedule-${state.createdSchedules!.length + 1}`,
          title: body.title ?? 'Extracted schedule',
          description: '',
          startTime: body.startTime ?? '2026-06-28T09:00:00',
          endTime: null,
          allDay: false,
          rrule: null,
          reminderMinutes: 0,
          status: 'pending',
          notes: [],
        };
        state.createdSchedules!.push(created);
        await route.fulfill({ json: created });
        return;
      }
      await route.fulfill({ json: [] });
      return;
    }

    if (path === '/api/folders') {
      await route.fulfill({ json: [] });
      return;
    }

    if (path === '/api/notes/trash') {
      await route.fulfill({ json: [trashNote] });
      return;
    }

    if (path === '/api/notes/trash-1/permanent' && request.method() === 'DELETE') {
      state.permanentDeleteRequests!.push('trash-1');
      await route.fulfill({ status: 204, body: '' });
      return;
    }

    if (path === '/api/notes' && request.method() === 'GET') {
      await route.fulfill({ json: seededNotes });
      return;
    }

    if (path === '/api/notes' && request.method() === 'POST') {
      const body = request.postDataJSON() as { title?: string; content?: string; tags?: string[] };
      await route.fulfill({
        json: {
          ...seededNotes[0],
          id: 'note-2',
          title: body.title ?? 'Untitled',
          content: body.content ?? '',
          tags: body.tags?.map((name, index) => ({ id: `tag-${index + 10}`, name })) ?? [],
          pinned: false,
          updatedAt: '2026-06-27T07:00:00Z',
        },
      });
      return;
    }

    if (path === '/api/ai/smart-chat' && request.method() === 'POST') {
      const body = request.postDataJSON() as { query?: string; message?: string };
      const query = body.query ?? body.message ?? '';
      const destructive = query.includes('清空回收站') || query.toLowerCase().includes('empty trash');
      const response = destructive
        ? {
            type: 'direct',
            route: 'DIRECT_AGENT',
            content: 'Empty trash requires confirmation.',
            actionJson: JSON.stringify([{ type: 'EMPTY_TRASH', count: 1 }]),
            sources: {},
          }
        : {
            type: 'direct',
            route: 'DIRECT_AGENT',
            content: 'Based on [note 1], roadmap summary is ready.',
            sources: { 1: { id: 'note-1', title: 'Roadmap review' } },
          };
      await route.fulfill({ json: response });
      return;
    }

    if (path === '/api/ai/extract-schedules' && request.method() === 'POST') {
      await route.fulfill({
        json: {
          schedules: [
            {
              title: 'Roadmap sync',
              startTime: '2026-06-28T09:00:00',
              endTime: '2026-06-28T09:30:00',
              allDay: false,
              rrule: null,
              confidence: 0.93,
              source: 'Finalize AI note workflow coverage.',
            },
          ],
        },
      });
      return;
    }

    if (path === '/api/ai/action-feedback' && request.method() === 'POST') {
      state.actionFeedbackRequests!.push(request.postDataJSON());
      await route.fulfill({ json: { content: 'Cancelled empty trash.' } });
      return;
    }

    if (path === '/api/ai/chat/history') {
      await route.fulfill({ json: { items: [], nextCursor: null, hasMore: false } });
      return;
    }

    await route.fulfill({ status: 404, json: { message: `Unhandled E2E route: ${path}` } });
  });
}

async function authenticate(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'e2e-token');
    localStorage.setItem(
      'auth-storage',
      JSON.stringify({
        state: {
          token: 'e2e-token',
          user: { userId: 'user-1', username: 'writer', email: 'writer@example.test' },
          isAuthenticated: true,
        },
        version: 0,
      }),
    );
  });
}

test('login loads the workspace with seeded notes', async ({ page }) => {
  await mockBackend(page);

  await page.goto('/login');
  await page.locator('input[type="text"]').fill('writer');
  await page.locator('input[type="password"]').fill('correct-password');
  await page.locator('button[type="submit"]').click();

  await expect(page).toHaveURL('/');
  await expect(page.getByRole('listbox', { name: 'Notes' })).toBeVisible();
  await expect(page.getByText('Roadmap review')).toBeVisible();
});

test('authenticated user can create a note without a real backend', async ({ page }) => {
  await mockBackend(page);
  await authenticate(page);

  await page.goto('/', { waitUntil: 'commit' });
  await expect(page.getByText('Roadmap review')).toBeVisible();

  await page.locator('.note-list-create-btn').click();

  await expect(page.getByText('Untitled')).toBeVisible();
  await expect(page.locator('.col-span-full')).toContainText('Note created');
});

test('AI smart chat renders completed content and clickable source references', async ({ page }) => {
  await mockBackend(page);
  await authenticate(page);

  await page.goto('/');
  await page.getByPlaceholder('问墨子任何事...').fill('总结路线图');
  await page.getByTitle('发送').click();

  await expect(page.getByRole('log')).toContainText('Based on');
  await expect(page.getByText('[1] Roadmap review')).toBeVisible();
  await page.getByText('[1] Roadmap review').click();
  await expect(page.getByRole('option', { name: /Roadmap review/ })).toHaveAttribute('aria-selected', 'true');
});

test('schedule extraction confirmation creates schedules in the workspace', async ({ page }) => {
  const state: MockState = {};
  await mockBackend(page, state);
  await authenticate(page);

  await page.goto('/');
  await page.getByRole('option', { name: /Roadmap review/ }).click();
  await page.getByTitle('提取日程').click();

  await expect(page.getByText('AI 提取日程')).toBeVisible();
  await expect(page.getByText('Roadmap sync')).toBeVisible();
  await page.getByRole('button', { name: '创建日程' }).click();

  await expect(page.locator('.col-span-full')).toContainText('已创建 1 个日程');
  expect(state.createdSchedules).toHaveLength(1);
});

test('destructive AI action stays pending until the user confirms or cancels', async ({ page }) => {
  const state: MockState = {};
  await mockBackend(page, state);
  await authenticate(page);

  await page.goto('/', { waitUntil: 'commit' });
  await page.getByPlaceholder('问墨子任何事...').fill('请清空回收站');
  await page.getByTitle('发送').click();

  await expect(page.getByText('PENDING_ACTION · 需要你确认')).toBeVisible();
  await expect(page.getByText('不会自动执行')).toBeVisible();
  expect(state.actionFeedbackRequests).toHaveLength(0);

  await page.getByRole('button', { name: '取消' }).click();
  await expect(page.getByText('Cancelled empty trash.')).toBeVisible();
  expect(state.actionFeedbackRequests).toHaveLength(1);
  expect(state.permanentDeleteRequests).toHaveLength(0);
});

test('invalid token state is rejected and logout clears the session', async ({ page }) => {
  await mockBackend(page);
  await page.addInitScript(() => {
    localStorage.setItem('token', 'expired-token');
    localStorage.removeItem('auth-storage');
  });

  await page.goto('/', { waitUntil: 'domcontentloaded' });

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.locator('input[type="password"]')).toBeVisible();

  await page.locator('input[type="text"]').fill('writer');
  await page.locator('input[type="password"]').fill('correct-password');
  await page.locator('button[type="submit"]').click();

  await expect(page).toHaveURL('/');
  await expect(page.getByText('Roadmap review')).toBeVisible();

  await page.getByRole('button', { name: 'Log out' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect.poll(() => page.evaluate(() => localStorage.getItem('token'))).toBeNull();
});
