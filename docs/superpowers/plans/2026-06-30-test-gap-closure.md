# Test Gap Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

## Execution Status - 2026-06-30

| ID | Status | Evidence |
|---|---|---|
| G1 LinkPreview controller contract | Done | `LinkPreviewControllerTest` added and release verification passed |
| G2 Backend SSE send-level error and disconnect | Done | AI stream error/cancel tests added and release verification passed |
| G3 Full-stack Playwright E2E | Done | `frontend/e2e/fullstack.spec.ts` passed in release verification |
| G4 k6 performance gate | Done | local, SSE 100, LLM, Agent saturation, and scheduler soak profiles are defined; release wrapper gates local/SSE/LLM/saturation and `RagVectorIndexPerformanceIT` closes the 10k local RAG retrieval gate |
| G5 Real LLM/embedding smoke | Done | `scripts/verify-real-ai.ps1` passed in release verification |
| G6 Frontend real rendering depth | Done | real Tiptap/App shell tests and coverage gate passed |
| G7 Release verification wrapper | Done | `scripts/verify-release.ps1` exits 0, starts a temp pgvector database, and makes skipped Maven tests fatal |
| G8 TEST_PLAN status sync | Done | `docs/TEST_PLAN.md` status override and `docs/TEST_GAP_CLOSURE_REPORT.md` added |
| G9 Skills support | Done | existing superpowers skills were sufficient; no external skill install needed |

Remaining strict open items:

- None from this audit pass.

**Goal:** Close the remaining test gaps by separating missing tests, weak mock-heavy tests, and real-environment verification into explicit, repeatable gates.

**Architecture:** Keep existing unit and MockMvc coverage, then add thin missing controller tests, backend SSE event/cancel tests, a real full-stack Playwright suite, and corrected k6 profiles. The release gate must fail when Docker, k6, backend, frontend, DeepSeek, TEI embedding, or performance SLOs are unavailable or failing.

**Tech Stack:** Spring Boot 3.2.5, JUnit 5, MockMvc, Testcontainers, PostgreSQL pgvector, React 18, Vitest, Playwright, k6, DeepSeek, Hugging Face TEI.

---

## Current Audit Snapshot

Evidence gathered on 2026-06-30:

- Backend Maven XML reports currently show `tests=734`, `failures=0`, `errors=0`, `skipped=0`.
- Docker is available and containers are running: `ainote-postgres`, `ainote-redis`, `ainote-neo4j`, `ainote-embedding`.
- k6 is installed: `k6.exe v1.2.3`.
- `SchemaValidationIT` and the other database integration tests execute through the release wrapper temp pgvector database; skipped Maven tests are fatal.
- Existing Playwright suite at `frontend/e2e/ainote.spec.ts` mocks all `/api/**` routes, so it is a frontend journey test, not a full-stack E2E test.
- k6 local, scheduler soak, SSE 100, LLM, and Agent saturation profiles are separated so local SLOs, long-run stability, streaming concurrency, provider latency, and overload behavior are reported separately.
- Backend `/api/ai/chat/stream` now has direct coverage for token/progress/complete, error event, and client-error cancellation.
- `LinkPreviewController` has controller contract tests.
- Frontend tests include real App shell and Tiptap rendering depth.

## Gap Inventory

| ID | Area | Status | Why It Is Not Complete |
|---|---|---|---|
| G1 | LinkPreview controller contract | Closed | `LinkPreviewControllerTest` verifies controller status codes and request validation. |
| G2 | Backend SSE send-level error and disconnect | Closed | token/progress/complete, error event, and client-error cancellation are covered. |
| G3 | Full-stack Playwright E2E | Closed | non-mocked full-stack Playwright verifies Spring Boot, JWT, DB persistence, and frontend journey. |
| G4 | k6 performance gate | Closed | local, SSE 100, LLM, Agent saturation, and scheduler soak profiles are defined; 10k local RAG vector retrieval is enforced by `RagVectorIndexPerformanceIT`. |
| G5 | Real LLM/embedding repeatable smoke | Closed | DeepSeek chat/SSE and local TEI embedding are repeatable release gates. |
| G6 | Frontend real rendering depth | Closed | App and Tiptap tests render the real surfaces with minimal mocking. |
| G7 | Release verification wrapper | Closed | `scripts/verify-release.ps1` asserts Docker, temp DB, no skipped Maven tests, frontend build, E2E, real AI, and perf gates. |
| G8 | TEST_PLAN status sync | Closed | `docs/TEST_PLAN.md` status override and `docs/TEST_GAP_CLOSURE_REPORT.md` record the current evidence. |
| G9 | Skills support | Optional | Built-in skills are enough; optional public `grafana/skills@k6` may help k6 work. |

---

### Task 1: Add LinkPreviewController Contract Tests

**Files:**
- Create: `backend/src/test/java/com/ainote/app/controller/LinkPreviewControllerTest.java`
- Read: `backend/src/main/java/com/ainote/app/controller/LinkPreviewController.java`
- Read: `backend/src/main/java/com/ainote/app/service/LinkPreviewService.java`

- [ ] **Step 1: Write the controller test**

Create `backend/src/test/java/com/ainote/app/controller/LinkPreviewControllerTest.java`:

```java
package com.ainote.app.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainote.app.config.GlobalExceptionHandler;
import com.ainote.app.service.LinkPreviewService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LinkPreviewControllerTest {

    private LinkPreviewService linkPreviewService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        linkPreviewService = mock(LinkPreviewService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LinkPreviewController(linkPreviewService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getPreview_returnsPreviewForPublicUrl() throws Exception {
        when(linkPreviewService.fetchPreview(eq("https://example.com/article")))
                .thenReturn(Map.of(
                        "url", "https://example.com/article",
                        "title", "Example Article",
                        "description", "Preview text",
                        "imageUrl", "",
                        "siteName", "example.com",
                        "faviconUrl", "https://example.com/favicon.ico"));

        mockMvc.perform(get("/api/link-preview")
                        .param("url", "https://example.com/article"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.com/article"))
                .andExpect(jsonPath("$.title").value("Example Article"))
                .andExpect(jsonPath("$.siteName").value("example.com"));

        verify(linkPreviewService).fetchPreview("https://example.com/article");
    }

    @Test
    void getPreview_requiresUrlParameter() throws Exception {
        mockMvc.perform(get("/api/link-preview"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPreview_returnsSafeFallbackWhenServiceRejectsPrivateUrl() throws Exception {
        when(linkPreviewService.fetchPreview(eq("http://127.0.0.1/admin")))
                .thenReturn(Map.of(
                        "url", "http://127.0.0.1/admin",
                        "title", "http://127.0.0.1/admin",
                        "description", "",
                        "imageUrl", "",
                        "siteName", "",
                        "faviconUrl", ""));

        mockMvc.perform(get("/api/link-preview")
                        .param("url", "http://127.0.0.1/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("http://127.0.0.1/admin"))
                .andExpect(jsonPath("$.description").value(""));
    }
}
```

- [ ] **Step 2: Run the focused test**

Run:

```powershell
cd D:\ainotetest\backend
mvn -q -o test -Dtest=LinkPreviewControllerTest
```

Expected: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 3: Commit**

```powershell
git add backend/src/test/java/com/ainote/app/controller/LinkPreviewControllerTest.java
git commit -m "test: add link preview controller contract coverage"
```

---

### Task 2: Strengthen Backend SSE Event and Cancellation Tests

**Files:**
- Modify: `backend/src/test/java/com/ainote/app/controller/AiControllerEndpointSupplementTest.java`
- Modify: `backend/src/test/java/com/ainote/app/controller/AiControllerTest.java`
- Read: `backend/src/main/java/com/ainote/app/controller/AiController.java`

- [ ] **Step 1: Add error event test to `AiControllerEndpointSupplementTest`**

Append this test near `chatStreamEmitsTokenProgressAndCompleteEvents`:

```java
@Test
void chatStreamEmitsErrorEventWhenOrchestratorReportsError() throws Exception {
    AtomicReference<Runnable> streamWork = new AtomicReference<>();
    doAnswer(invocation -> {
        streamWork.set(invocation.getArgument(0));
        return null;
    }).when(executorService).execute(any(Runnable.class));
    when(agentService.createCancelToken(eq("user-1"), anyString()))
            .thenReturn(new CancellationToken());
    doAnswer(invocation -> {
        StreamCallback callback = invocation.getArgument(4, StreamCallback.class);
        callback.onError("model unavailable");
        return null;
    }).when(chatOrchestrator).chatStream(
            eq("hello"),
            eq(List.of()),
            eq("user-1"),
            anyString(),
            any(StreamCallback.class));

    MvcResult result = mockMvc.perform(post("/api/ai/chat/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("query", "hello"))))
            .andExpect(request().asyncStarted())
            .andReturn();

    streamWork.get().run();

    mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().string(containsString("event:error")))
            .andExpect(content().string(containsString("data:model unavailable")));
}
```

- [ ] **Step 2: Add direct completion cancellation test to `AiControllerTest`**

If `AiControllerTest` already has a helper constructor, reuse it. Otherwise add a direct test that calls `chatStream` and verifies the returned emitter can complete without losing the cancellation token.

```java
@Test
void chatStream_completionCancelsRequestToken() {
    AiChatRequest request = new AiChatRequest();
    request.setMessage("hello");
    CancellationToken token = new CancellationToken();
    when(securityUtils.getCurrentUserId()).thenReturn("user-123");
    when(agentService.createCancelToken(eq("user-123"), anyString())).thenReturn(token);
    doAnswer(invocation -> null).when(executorService).execute(any(Runnable.class));

    SseEmitter emitter = controller.chatStream(request);
    emitter.complete();

    assertThat(token.isCancelled()).isTrue();
}
```

If `SseEmitter.complete()` does not execute callbacks without a handler in this Spring version, introduce a minimal package-private `SseEmitterFactory` in production code and inject a test emitter that exposes completion callbacks. Do not weaken the assertion.

- [ ] **Step 3: Run focused tests**

Run:

```powershell
cd D:\ainotetest\backend
mvn -q -o test -Dtest=AiControllerEndpointSupplementTest,AiControllerTest
```

Expected: both classes pass with no skip.

- [ ] **Step 4: Commit**

```powershell
git add backend/src/test/java/com/ainote/app/controller/AiControllerEndpointSupplementTest.java backend/src/test/java/com/ainote/app/controller/AiControllerTest.java backend/src/main/java/com/ainote/app/controller/AiController.java
git commit -m "test: cover ai stream error and cancellation behavior"
```

---

### Task 3: Add Real Full-Stack Playwright E2E Suite

**Files:**
- Create: `frontend/e2e/fullstack.spec.ts`
- Modify: `frontend/playwright.config.ts`
- Read: `frontend/e2e/ainote.spec.ts`
- Read: `frontend/src/services/apiBase.ts`
- Read: `frontend/src/stores/authStore.ts`

- [ ] **Step 1: Keep existing mocked E2E, add a separate full-stack file**

Create `frontend/e2e/fullstack.spec.ts`:

```ts
import { expect, test } from '@playwright/test';

const backendURL = process.env.AINOTE_BACKEND_URL ?? 'http://127.0.0.1:8081';
const runFullstack = process.env.AINOTE_E2E_FULLSTACK === 'true';

test.describe('full-stack E2E', () => {
  test.skip(!runFullstack, 'Set AINOTE_E2E_FULLSTACK=true and start backend dependencies to run full-stack E2E');

  test.beforeEach(async ({ page }) => {
    await page.addInitScript((baseURL) => {
      window.localStorage.setItem('ainote.apiBaseUrl', baseURL as string);
    }, backendURL);
  });

  test('register login create edit refresh persists note', async ({ page, request }) => {
    const stamp = Date.now();
    const username = `e2e_${stamp}`;
    const email = `${username}@example.test`;
    const password = 'CodexE2E123!';

    const register = await request.post(`${backendURL}/api/auth/register`, {
      data: { username, email, password },
    });
    expect(register.ok()).toBeTruthy();
    const auth = await register.json();
    expect(auth.token).toBeTruthy();

    await page.goto('/');
    await page.evaluate((payload) => {
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

    await page.reload();
    await expect(page.getByRole('listbox', { name: 'Notes' })).toBeVisible();

    await page.locator('.note-list-create-btn').click();
    await expect(page.getByText('Untitled')).toBeVisible();

    const title = `Fullstack E2E ${stamp}`;
    await page.getByRole('textbox', { name: /title|标题/i }).fill(title);
    await page.keyboard.press('Tab');
    await page.keyboard.type('Persisted full-stack body');
    await expect(page.locator('.col-span-full')).toContainText(/saved|保存|created|创建/i);

    await page.reload();
    await expect(page.getByText(title)).toBeVisible();
  });

  test('real backend rejects expired token and redirects to login', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('token', 'expired.invalid.token');
      localStorage.removeItem('auth-storage');
    });

    await page.goto('/');

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.locator('input[type="password"]')).toBeVisible();
  });
});
```

- [ ] **Step 2: Make Playwright base URL configurable**

Modify `frontend/playwright.config.ts`:

```ts
const frontendURL = process.env.AINOTE_FRONTEND_URL ?? 'http://127.0.0.1:4174';

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: frontendURL,
    trace: 'retain-on-failure',
  },
  webServer: process.env.AINOTE_E2E_REUSE_FRONTEND === 'true'
    ? undefined
    : {
        command: 'npm run dev -- --host 127.0.0.1 --port 4174',
        url: frontendURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
```

- [ ] **Step 3: Run mocked E2E**

Run:

```powershell
cd D:\ainotetest\frontend
npm run test:e2e -- e2e/ainote.spec.ts
```

Expected: existing mocked E2E tests pass.

- [ ] **Step 4: Run full-stack E2E with backend already running**

Preconditions:

```powershell
docker ps --format "{{.Names}} {{.Status}}"
[Environment]::GetEnvironmentVariable("DEEPSEEK_API_KEY", "User") -ne $null
[Environment]::GetEnvironmentVariable("DEEPSEEK_EMBEDDING_BASE_URL", "User")
```

Run:

```powershell
cd D:\ainotetest\frontend
$env:AINOTE_E2E_FULLSTACK='true'
$env:AINOTE_BACKEND_URL='http://127.0.0.1:8081'
npm run test:e2e -- e2e/fullstack.spec.ts
```

Expected: full-stack tests pass against the real backend.

- [ ] **Step 5: Commit**

```powershell
git add frontend/e2e/fullstack.spec.ts frontend/playwright.config.ts
git commit -m "test: add real full-stack playwright coverage"
```

---

### Task 4: Replace k6 Mixed Metrics With Explicit Performance Profiles

**Files:**
- Modify: `perf/ainote-load.js`
- Create: `perf/README.md`
- Optional create: `perf/seed-rag.ps1`

- [ ] **Step 1: Split k6 metrics by path**

Modify `perf/ainote-load.js` to expose separate trends:

```js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const TOKEN = __ENV.AUTH_TOKEN || 'perf-test-token';
const PROFILE = __ENV.PERF_PROFILE || 'local';

const ragWarmLatency = new Trend('rag_warm_latency');
const ragColdWithRewriteLatency = new Trend('rag_cold_with_rewrite_latency');
const agentRouteLatency = new Trend('agent_route_latency');
const agentCompleteLatency = new Trend('agent_complete_latency');
const notesLatency = new Trend('notes_pagination_latency');
const schedulerLatency = new Trend('scheduler_latency');
const sseFirstTokenLatency = new Trend('sse_first_token_latency');
const sseCompleteLatency = new Trend('sse_complete_latency');
const sseAccepted = new Rate('sse_accepted');

export const options = {
  thresholds: {
    http_req_failed: ['rate<0.01'],
    notes_pagination_latency: ['p(95)<300'],
    scheduler_latency: ['p(95)<1000'],
    rag_warm_latency: ['p(95)<800'],
    sse_accepted: ['rate>0.99'],
  },
  scenarios: PROFILE === 'local' ? {
    rag_warm: { executor: 'constant-vus', exec: 'ragWarm', vus: Number(__ENV.RAG_VUS || 2), duration: __ENV.DURATION || '30s' },
    notes_pagination: { executor: 'constant-vus', exec: 'notesPagination', vus: Number(__ENV.NOTES_VUS || 5), duration: __ENV.DURATION || '30s' },
    scheduler: { executor: 'constant-vus', exec: 'scheduler', vus: Number(__ENV.SCHEDULER_VUS || 2), duration: __ENV.DURATION || '30s' },
  } : {
    rag_cold_with_rewrite: { executor: 'constant-vus', exec: 'ragColdWithRewrite', vus: Number(__ENV.RAG_COLD_VUS || 1), duration: __ENV.DURATION || '30s' },
    agent_route: { executor: 'constant-vus', exec: 'agentRoute', vus: Number(__ENV.ROUTE_VUS || 1), duration: __ENV.DURATION || '30s' },
    agent_complete: { executor: 'constant-vus', exec: 'agentComplete', vus: Number(__ENV.AGENT_VUS || 1), duration: __ENV.DURATION || '30s' },
    sse: { executor: 'constant-vus', exec: 'sseChat', vus: Number(__ENV.SSE_VUS || 10), duration: __ENV.DURATION || '30s' },
  },
};

function headers(extra = {}) {
  return {
    headers: {
      Authorization: `Bearer ${TOKEN}`,
      'Content-Type': 'application/json',
      ...extra,
    },
  };
}

export function ragWarm() {
  const response = http.get(`${BASE_URL}/api/notes/search?q=planning`, headers());
  ragWarmLatency.add(response.timings.duration);
  check(response, { 'rag warm responds': (res) => res.status === 200 });
  sleep(1);
}

export function ragColdWithRewrite() {
  const query = `planning-${Date.now()}-${__VU}-${__ITER}`;
  const response = http.get(`${BASE_URL}/api/notes/search?q=${encodeURIComponent(query)}`, headers());
  ragColdWithRewriteLatency.add(response.timings.duration);
  check(response, { 'rag cold responds': (res) => res.status === 200 });
  sleep(1);
}

export function agentRoute() {
  const response = http.post(`${BASE_URL}/api/ai/route`, JSON.stringify({ query: 'Create a short project plan', noteIds: [] }), headers());
  agentRouteLatency.add(response.timings.duration);
  check(response, { 'route responds': (res) => res.status === 200 });
  sleep(1);
}

export function agentComplete() {
  const response = http.post(`${BASE_URL}/api/ai/smart-chat`, JSON.stringify({ query: 'Create a short project plan', noteIds: [] }), headers());
  agentCompleteLatency.add(response.timings.duration);
  check(response, { 'agent responds or overloads': (res) => [200, 202, 503].includes(res.status) });
  sleep(1);
}

export function notesPagination() {
  const page = (__ITER % 5) + 1;
  const response = http.get(`${BASE_URL}/api/notes?page=${page}&size=25`, headers());
  notesLatency.add(response.timings.duration);
  check(response, { 'notes page responds': (res) => res.status === 200 });
  sleep(1);
}

export function scheduler() {
  const response = http.get(`${BASE_URL}/api/ai/schedules`, headers());
  schedulerLatency.add(response.timings.duration);
  check(response, { 'scheduler responds': (res) => res.status === 200 });
  sleep(1);
}

export function sseChat() {
  const started = Date.now();
  const response = http.post(
    `${BASE_URL}/api/ai/chat/stream`,
    JSON.stringify({ message: 'Summarize current notes', scope: 'all' }),
    headers({ Accept: 'text/event-stream' }),
  );
  sseAccepted.add(response.status === 200 || response.status === 204);
  const firstTokenIndex = response.body ? response.body.indexOf('event:token') : -1;
  if (firstTokenIndex >= 0) {
    sseFirstTokenLatency.add(response.timings.waiting);
  }
  sseCompleteLatency.add(Date.now() - started);
  check(response, { 'sse stream accepted': (res) => [200, 204].includes(res.status) });
  sleep(1);
}
```

- [ ] **Step 2: Add perf README with exact gates**

Create `perf/README.md`:

```markdown
# AI Note Performance Gates

## Profiles

`PERF_PROFILE=local` verifies local backend, DB, Redis, scheduler, and warm RAG search. It must not call new DeepSeek query rewriting for every iteration.

`PERF_PROFILE=llm` verifies real DeepSeek route/agent/SSE paths. It reports external provider latency separately from local SLOs.

## Commands

Local warm RAG:

```powershell
$env:BASE_URL='http://127.0.0.1:8081'
$env:AUTH_TOKEN='<jwt>'
$env:PERF_PROFILE='local'
$env:DURATION='30s'
k6 run perf/ainote-load.js
```

Real LLM smoke:

```powershell
$env:BASE_URL='http://127.0.0.1:8081'
$env:AUTH_TOKEN='<jwt>'
$env:PERF_PROFILE='llm'
$env:DURATION='30s'
$env:SSE_VUS='10'
k6 run perf/ainote-load.js
```

## Pass Criteria

- Local: `http_req_failed < 1%`, notes p95 < 300ms, scheduler p95 < 1000ms, warm RAG p95 < 800ms.
- LLM: checks must pass and provider latencies must be reported. Do not apply local 800ms/2s SLOs to full DeepSeek responses.
- Release: if LLM smoke fails by status code, release is blocked. If only provider latency exceeds the observed baseline, open a performance risk item.
```

- [ ] **Step 3: Run local profile**

Run:

```powershell
cd D:\ainotetest
$env:BASE_URL='http://127.0.0.1:8081'
$env:AUTH_TOKEN=(Get-Content "$env:TEMP\ainote-real-run\token.txt" -Raw).Trim()
$env:PERF_PROFILE='local'
$env:DURATION='30s'
k6 run perf\ainote-load.js
```

Expected: local profile passes thresholds.

- [ ] **Step 4: Run LLM profile**

Run:

```powershell
cd D:\ainotetest
$env:BASE_URL='http://127.0.0.1:8081'
$env:AUTH_TOKEN=(Get-Content "$env:TEMP\ainote-real-run\token.txt" -Raw).Trim()
$env:PERF_PROFILE='llm'
$env:DURATION='30s'
$env:SSE_VUS='10'
k6 run perf\ainote-load.js
```

Expected: checks pass; provider latency is reported separately.

- [ ] **Step 5: Commit**

```powershell
git add perf/ainote-load.js perf/README.md
git commit -m "test: split local and llm performance gates"
```

---

### Task 5: Add Repeatable Real DeepSeek and TEI Smoke Script

**Files:**
- Create: `scripts/verify-real-ai.ps1`
- Modify: `.gitignore` only if generated output paths are missing

- [ ] **Step 1: Create script**

Create `scripts/verify-real-ai.ps1`:

```powershell
param(
  [string]$BaseUrl = "http://127.0.0.1:8081",
  [string]$EmbeddingUrl = "http://127.0.0.1:8082/v1"
)

$ErrorActionPreference = "Stop"
$runDir = Join-Path $env:TEMP "ainote-real-run"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("DEEPSEEK_API_KEY", "User"))) {
  throw "DEEPSEEK_API_KEY is missing from the Windows User environment"
}

$embeddingBody = '{"input":"hello 中文","model":"Qwen/Qwen3-Embedding-0.6B","encoding_format":"float"}'
$embedding = Invoke-RestMethod -Uri "$EmbeddingUrl/embeddings" -Method Post -ContentType "application/json" -Body $embeddingBody -TimeoutSec 60
$dimension = $embedding.data[0].embedding.Count
if ($dimension -ne 1024) {
  throw "Expected 1024 embedding dimensions but got $dimension"
}

$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$username = "real_ai_$stamp"
$email = "$username@example.test"
$password = "CodexReal123!"

$auth = Invoke-RestMethod -Uri "$BaseUrl/api/auth/register" -Method Post -ContentType "application/json" -Body (@{
  username = $username
  email = $email
  password = $password
} | ConvertTo-Json -Compress) -TimeoutSec 60

if ([string]::IsNullOrWhiteSpace($auth.token)) {
  throw "Register returned empty token"
}

$headers = @{ Authorization = "Bearer $($auth.token)" }
$note = Invoke-RestMethod -Uri "$BaseUrl/api/notes" -Method Post -ContentType "application/json" -Headers $headers -Body (@{
  title = "Real AI smoke $stamp"
  content = "Verify DeepSeek chat and local TEI embedding write-through. 中文向量验证 $stamp"
  tags = @("real-ai-smoke")
} | ConvertTo-Json -Compress) -TimeoutSec 60

$chat = Invoke-RestMethod -Uri "$BaseUrl/api/ai/chat" -Method Post -ContentType "application/json" -Headers $headers -Body (@{
  message = "Reply with exactly: real AI smoke OK"
  scope = "selected"
  noteIds = @($note.id)
} | ConvertTo-Json -Compress) -TimeoutSec 180

$text = [string]$chat.content
if ($text -notmatch "real AI smoke OK") {
  throw "Unexpected chat response: $text"
}

Set-Content -Path (Join-Path $runDir "token.txt") -Value $auth.token
Set-Content -Path (Join-Path $runDir "note.txt") -Value $note.id
Write-Output "real-ai-smoke=passed"
Write-Output "embedding-dimension=$dimension"
Write-Output "note-id=$($note.id)"
```

- [ ] **Step 2: Run smoke**

Run:

```powershell
cd D:\ainotetest
powershell -ExecutionPolicy Bypass -File scripts\verify-real-ai.ps1
```

Expected:

```text
real-ai-smoke=passed
embedding-dimension=1024
note-id=<uuid>
```

- [ ] **Step 3: Commit**

```powershell
git add scripts/verify-real-ai.ps1
git commit -m "test: add repeatable real ai smoke verification"
```

---

### Task 6: Add Release Verification Wrapper

**Files:**
- Create: `scripts/verify-release.ps1`
- Read: `backend/pom.xml`
- Read: `frontend/package.json`
- Read: `perf/README.md`

- [ ] **Step 1: Create wrapper**

Create `scripts/verify-release.ps1`:

```powershell
param(
  [switch]$SkipLlmPerf
)

$ErrorActionPreference = "Stop"

function Require-Command($Name) {
  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
    throw "Required command not found: $Name"
  }
}

Require-Command docker
Require-Command mvn
Require-Command npm

if (Get-Command k6 -ErrorAction SilentlyContinue) {
  $k6 = "k6"
} elseif (Test-Path "C:\Program Files\k6\k6.exe") {
  $k6 = "C:\Program Files\k6\k6.exe"
} else {
  throw "k6 is required for release verification"
}

docker ps | Out-Host

Push-Location "D:\ainotetest\backend"
mvn -q -o clean verify "-Ddocker.host=tcp://127.0.0.1:2375" "-Dapi.version=1.44" "-Ddocker.tls.verify=false"
Pop-Location

Push-Location "D:\ainotetest\frontend"
npm test
npm run build
Pop-Location

powershell -ExecutionPolicy Bypass -File "D:\ainotetest\scripts\verify-real-ai.ps1"

$env:BASE_URL = "http://127.0.0.1:8081"
$env:AUTH_TOKEN = (Get-Content "$env:TEMP\ainote-real-run\token.txt" -Raw).Trim()
$env:PERF_PROFILE = "local"
$env:DURATION = "30s"
& $k6 run "D:\ainotetest\perf\ainote-load.js"

if (-not $SkipLlmPerf) {
  $env:PERF_PROFILE = "llm"
  $env:DURATION = "30s"
  $env:SSE_VUS = "10"
  & $k6 run "D:\ainotetest\perf\ainote-load.js"
}

Write-Output "release-verification=passed"
```

- [ ] **Step 2: Run wrapper**

Run:

```powershell
cd D:\ainotetest
powershell -ExecutionPolicy Bypass -File scripts\verify-release.ps1
```

Expected: exits 0 and prints `release-verification=passed`.

- [ ] **Step 3: Commit**

```powershell
git add scripts/verify-release.ps1
git commit -m "test: add release verification wrapper"
```

---

### Task 7: Reduce Frontend Mock Blind Spots

**Files:**
- Modify: `frontend/tests/tiptap-editor.behavior.test.tsx`
- Create: `frontend/tests/tiptap-editor.real.behavior.test.tsx`
- Create: `frontend/tests/app-shell.real.behavior.test.tsx`
- Read: `frontend/src/components/TiptapEditor.tsx`
- Read: `frontend/src/App.tsx`

- [ ] **Step 1: Keep mock-heavy Tiptap test but add real smoke**

Create `frontend/tests/tiptap-editor.real.behavior.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TiptapEditor from '../src/components/TiptapEditor';

beforeEach(() => {
  vi.clearAllMocks();
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver;
});

describe('TiptapEditor real integration smoke', () => {
  it('renders real editor content and emits debounced markdown changes', async () => {
    vi.useFakeTimers();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const onChange = vi.fn();

    render(
      <TiptapEditor
        content="Initial"
        onChange={onChange}
        onEditorReady={vi.fn()}
      />,
    );

    expect(await screen.findByText('Initial')).toBeInTheDocument();
    await user.click(screen.getByText('Initial'));
    await user.keyboard(' updated');
    vi.advanceTimersByTime(600);

    await waitFor(() => expect(onChange).toHaveBeenCalled());
    vi.useRealTimers();
  });
});
```

If the current component props differ, adapt only the prop names to the actual `TiptapEditor` signature and keep the behavior assertion.

- [ ] **Step 2: Add real App shell test with fewer child mocks**

Create `frontend/tests/app-shell.real.behavior.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../src/App';
import * as api from '../src/api';

vi.mock('../src/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../src/api')>();
  return {
    ...actual,
    getNotes: vi.fn(),
    getFolders: vi.fn(),
    getSchedules: vi.fn(),
    getChatHistory: vi.fn(),
  };
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.getNotes).mockResolvedValue([]);
  vi.mocked(api.getFolders).mockResolvedValue([]);
  vi.mocked(api.getSchedules).mockResolvedValue([]);
  vi.mocked(api.getChatHistory).mockResolvedValue([]);
  localStorage.setItem('token', 'test-token');
  localStorage.setItem('auth-storage', JSON.stringify({
    state: {
      token: 'test-token',
      user: { userId: 'user-1', username: 'tester', email: 'tester@example.test' },
      isAuthenticated: true,
    },
    version: 0,
  }));
});

describe('App real shell behavior', () => {
  it('loads real shell and startup data without replacing core layout components', async () => {
    render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
    );

    await waitFor(() => expect(api.getNotes).toHaveBeenCalled());
    expect(screen.getByRole('listbox', { name: /notes/i })).toBeInTheDocument();
  });
});
```

If router ownership currently lives inside `App`, remove `MemoryRouter` and render `<App />` directly.

- [ ] **Step 3: Run frontend tests**

Run:

```powershell
cd D:\ainotetest\frontend
npm test
npm run test:coverage
npm run build
```

Expected: behavior tests pass, line coverage remains at or above 50, build exits 0.

- [ ] **Step 4: Commit**

```powershell
git add frontend/tests/tiptap-editor.real.behavior.test.tsx frontend/tests/app-shell.real.behavior.test.tsx
git commit -m "test: add real frontend rendering smoke coverage"
```

---

### Task 8: Sync TEST_PLAN and Add Verification Record

**Files:**
- Modify: `docs/TEST_PLAN.md`
- Create: `docs/TEST_GAP_CLOSURE_REPORT.md`

- [ ] **Step 1: Update stale matrix states**

In `docs/TEST_PLAN.md`, update only rows whose tests now exist and have passed:

```markdown
| TC-API-06 | P1 | ... | ... | ... | ✅ `GraphControllerTest` |
| TC-API-07 | P1 | ... | ... | ... | ✅ `LinkPreviewControllerTest` |
```

Update §3.10 and §3.17 rows from `⏳` to one of:

- `✅` when there is a real behavior test that renders the actual component or hook.
- `◐` when coverage exists but uses heavy mocks or only verifies shell state.
- `⏳` when no relevant test exists.

- [ ] **Step 2: Create closure report**

Create `docs/TEST_GAP_CLOSURE_REPORT.md`:

```markdown
# Test Gap Closure Report

## Completed

- Backend unit/MockMvc/IT: `mvn -q -o clean verify ...` passed with 0 skipped.
- Frontend unit/build: `npm test`, `npm run test:coverage`, `npm run build` passed.
- Real AI smoke: `scripts/verify-real-ai.ps1` passed.
- Local k6 profile: `PERF_PROFILE=local` passed.
- LLM k6 profile: `PERF_PROFILE=llm` completed with status checks passing.
- Full-stack E2E: `AINOTE_E2E_FULLSTACK=true npm run test:e2e -- e2e/fullstack.spec.ts` passed.

## Remaining Risks

- DeepSeek provider latency is external; track baseline separately from local RAG SLO.
- Release runs require Docker Desktop with API compatibility settings.
- Rotate any API key exposed in chat or logs after verification.
```

- [ ] **Step 3: Run final verification**

Run:

```powershell
cd D:\ainotetest
powershell -ExecutionPolicy Bypass -File scripts\verify-release.ps1
```

Expected: exits 0.

- [ ] **Step 4: Commit**

```powershell
git add docs/TEST_PLAN.md docs/TEST_GAP_CLOSURE_REPORT.md
git commit -m "docs: sync test plan gap closure status"
```

---

### Task 9: Decide on Optional External Skills

**Files:**
- None unless the user asks to install a skill.

- [ ] **Step 1: Use built-in skills by default**

Current local skills are enough:

- `superpowers:writing-plans` for this plan.
- `superpowers:subagent-driven-development` for parallel implementation.
- `superpowers:executing-plans` for inline execution.
- `superpowers:verification-before-completion` before claiming done.
- `superpowers:systematic-debugging` for k6 or E2E failures.

- [ ] **Step 2: Optionally install k6 skill**

Only if the user explicitly asks to install:

```powershell
npx skills add grafana/skills@k6 -g -y
```

Search evidence: `grafana/skills@k6` has about 1.3K installs and is more credible than smaller search results.

- [ ] **Step 3: Do not install low-confidence E2E skills by default**

Search found E2E skills with lower install counts. Use local Playwright knowledge unless the user wants a specific external skill:

- `sickn33/antigravity-awesome-skills@e2e-testing` about 171 installs.
- `asyrafhussin/agent-skills@e2e-playwright-testing` about 134 installs.

---

## Final Verification Matrix

Run these in order before declaring completion:

```powershell
cd D:\ainotetest\backend
mvn -q -o clean verify "-Ddocker.host=tcp://127.0.0.1:2375" "-Dapi.version=1.44" "-Ddocker.tls.verify=false"
```

```powershell
cd D:\ainotetest\frontend
npm test
npm run test:coverage
npm run build
```

```powershell
cd D:\ainotetest
powershell -ExecutionPolicy Bypass -File scripts\verify-real-ai.ps1
```

```powershell
cd D:\ainotetest\frontend
$env:AINOTE_E2E_FULLSTACK='true'
$env:AINOTE_BACKEND_URL='http://127.0.0.1:8081'
npm run test:e2e -- e2e/fullstack.spec.ts
```

```powershell
cd D:\ainotetest
$env:BASE_URL='http://127.0.0.1:8081'
$env:AUTH_TOKEN=(Get-Content "$env:TEMP\ainote-real-run\token.txt" -Raw).Trim()
$env:PERF_PROFILE='local'
k6 run perf\ainote-load.js
```

```powershell
cd D:\ainotetest
$env:BASE_URL='http://127.0.0.1:8081'
$env:AUTH_TOKEN=(Get-Content "$env:TEMP\ainote-real-run\token.txt" -Raw).Trim()
$env:PERF_PROFILE='llm'
$env:SSE_VUS='10'
k6 run perf\ainote-load.js
```

## Self-Review

- Spec coverage: Tasks map to LinkPreview controller, SSE backend gaps, full-stack E2E, k6 performance, real AI smoke, frontend mock blind spots, release wrapper, and documentation sync.
- Placeholder scan: No `TBD`, `TODO`, or "implement later" instructions remain.
- Type consistency: Java snippets use existing `AiController`, `StreamCallback`, `CancellationToken`, `AiChatRequest`, and MockMvc patterns. TypeScript snippets use Playwright, Vitest, and current project conventions.
