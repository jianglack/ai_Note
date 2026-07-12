# Memory Explainability UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build task 4: productized memory explainability in the user memory page, plus a guarded admin review queue backed by the task 3 feedback loop.

**Architecture:** Keep `MemoryControlPanel` as the settings-page entry point and split responsibility inside the component into two workspaces: `My Memories` and `Review Queue`. Add only one backend response field, `metadataJson`, because the database and governance writer already persist explanation metadata. Keep admin API loading lazy so ordinary users do not hit admin endpoints on page load.

**Tech Stack:** Spring Boot MVC, Java records, JUnit/MockMvc/Mockito, React 18, TypeScript, Vitest, Testing Library, Heroicons, existing settings CSS.

---

## Files

- Modify: `backend/src/main/java/com/ainote/app/model/memory/MemoryResponse.java`
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryControlService.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryControlServiceTest.java`
- Modify: `backend/src/test/java/com/ainote/app/controller/MemoryControllerTest.java`
- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/components/MemoryControlPanel.tsx`
- Modify: `frontend/src/components/SettingsPage.css`
- Modify: `frontend/tests/memory-control-panel.behavior.test.tsx`

No database migration is required.

---

### Task 1: Backend Memory Metadata Contract

**Files:**
- Modify: `backend/src/main/java/com/ainote/app/model/memory/MemoryResponse.java`
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryControlService.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryControlServiceTest.java`
- Modify: `backend/src/test/java/com/ainote/app/controller/MemoryControllerTest.java`

- [ ] **Step 1: Write failing service test**

Add a test in `MemoryControlServiceTest` proving `metadataJson` from `SemanticMemory` is returned in `MemoryResponse`:

```java
@Test
void listMemoriesReturnsGovernanceMetadataJson() {
    SemanticMemory memory = memory("user-1", 21L, "active");
    memory.setMetadataJson("{\"capture_reason\":\"explicit memory request\",\"policy_signals\":[\"explicit_remember\"]}");
    when(semanticMemoryRepository.searchUserMemories(
            eq("user-1"),
            eq(null),
            eq(null),
            eq(null),
            any(PageRequest.class)))
            .thenReturn(List.of(memory));

    MemoryListResponse response = service.listMemories("user-1", null, null, null, null);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).metadataJson())
            .isEqualTo("{\"capture_reason\":\"explicit memory request\",\"policy_signals\":[\"explicit_remember\"]}");
}
```

- [ ] **Step 2: Write failing controller test**

Extend `MemoryControllerTest.listMemoriesUsesCurrentUserAndFilters`:

```java
.andExpect(jsonPath("$.items[0].metadataJson").value("{\"capture_reason\":\"explicit memory request\"}"));
```

Update the `memoryResponse` helper to pass the metadata string once the record constructor is expanded.

- [ ] **Step 3: Verify RED**

Run:

```powershell
cd backend
mvn "-Dtest=MemoryControlServiceTest,MemoryControllerTest" test
```

Expected: fail because `MemoryResponse.metadataJson()` does not exist or the JSON field is absent.

- [ ] **Step 4: Implement minimal backend contract**

Add `String metadataJson` to `MemoryResponse` after `evidenceExcerpt`, and map `memory.getMetadataJson()` in `MemoryControlService.toResponse`.

- [ ] **Step 5: Verify GREEN**

Run:

```powershell
cd backend
mvn "-Dtest=MemoryControlServiceTest,MemoryControllerTest" test
```

Expected: both targeted backend test classes pass.

---

### Task 2: Frontend API Types And Admin Methods

**Files:**
- Modify: `frontend/src/api.ts`
- Modify: `frontend/tests/memory-control-panel.behavior.test.tsx`

- [ ] **Step 1: Write failing frontend tests through component mocks**

Extend the API mock object in `memory-control-panel.behavior.test.tsx` with:

```ts
submitMemoryFeedback: vi.fn(),
getMemoryReviewCases: vi.fn(),
decideMemoryReviewCase: vi.fn(),
exportMemoryReplayCandidates: vi.fn(),
```

The tests in later tasks will fail to compile until `frontend/src/api.ts` exports the matching types/functions.

- [ ] **Step 2: Implement API types and functions**

Add `metadataJson: string | null` to `MemoryRecord`.

Add:

```ts
export type MemoryFeedbackPayload = {
  feedbackType: string;
  userComment?: string | null;
  expectedContent?: string | null;
  expectedMemoryType?: string | null;
  expectedCaptureAllowed?: boolean | null;
};

export type MemoryReviewCaseRecord = {
  id: number;
  memoryId: number | null;
  userId: string | null;
  feedbackType: string;
  userComment: string | null;
  expectedContent: string | null;
  expectedMemoryType: string | null;
  expectedCaptureAllowed: boolean | null;
  status: string;
  reviewerId: string | null;
  reviewerDecision: string | null;
  reviewerComment: string | null;
  replayCaseId: string | null;
  replayCaseJson: string | null;
  manifestJson: string | null;
  memoryBeforeJson: string | null;
  sourceContextJson: string | null;
  policySnapshotJson: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  reviewedAt: string | null;
};
```

Add list/decision/export response types and functions for:

```ts
POST /api/memories/{id}/feedback
GET /api/admin/memory-review-cases
POST /api/admin/memory-review-cases/{id}/decision
GET /api/admin/memory-review-cases/replay-candidates
```

- [ ] **Step 3: Verify TypeScript compilation with tests**

Run:

```powershell
cd frontend
npm test -- memory-control-panel.behavior.test.tsx
```

Expected: current tests still pass or fail only on not-yet-implemented UI expectations from later tasks.

---

### Task 3: User Memory Explanation And Feedback Flow

**Files:**
- Modify: `frontend/src/components/MemoryControlPanel.tsx`
- Modify: `frontend/tests/memory-control-panel.behavior.test.tsx`
- Modify: `frontend/src/components/SettingsPage.css`

- [ ] **Step 1: Write failing explanation tests**

Add test data with:

```ts
metadataJson: JSON.stringify({
  capture_reason: 'explicit memory request',
  policy_reason: 'user asked the assistant to remember the preference',
  decision_type: 'allow',
  candidate_confidence: 0.91,
  policy_signals: ['explicit_remember', 'advisor_preference_signal'],
  policy_source: 'memory_write_service',
})
```

Assert the row renders:

```ts
expect(await screen.findByText('explicit memory request')).toBeInTheDocument();
expect(screen.getByText('user asked the assistant to remember the preference')).toBeInTheDocument();
expect(screen.getByText('advisor')).toBeInTheDocument();
expect(screen.getByText('explicit_remember')).toBeInTheDocument();
expect(screen.getByText('advisor_preference_signal')).toBeInTheDocument();
```

Add a legacy test with `metadataJson: '{bad json'` and assert `无结构化解释` and `unknown` render.

- [ ] **Step 2: Write failing per-memory event test**

Click the explanation/details control for memory `1` and assert:

```ts
expect(apiMocks.getMemoryEvents).toHaveBeenCalledWith({ memoryId: 1, limit: 25 });
```

The page must not call `getMemoryEvents({ limit: 50 })` on initial render.

- [ ] **Step 3: Write failing feedback test**

Open the report dialog, choose the wrong-memory reason, submit a correction, and assert:

```ts
expect(apiMocks.submitMemoryFeedback).toHaveBeenCalledWith(1, expect.objectContaining({
  feedbackType: 'wrong_memory',
  userComment: '这条记忆不准确',
  expectedContent: '用户希望回答更直接',
  expectedMemoryType: 'preference',
  expectedCaptureAllowed: true,
}));
```

- [ ] **Step 4: Verify RED**

Run:

```powershell
cd frontend
npm test -- memory-control-panel.behavior.test.tsx
```

Expected: fail because explanation UI, lazy per-memory event loading, and feedback dialog are not implemented.

- [ ] **Step 5: Implement user workspace**

Implement safe JSON parsing, metadata extraction, advisor inference, row expansion, per-memory event loading, and feedback dialog. Keep `My Memories` as the default workspace and preserve existing disable/delete/export/context-preview behavior.

- [ ] **Step 6: Verify GREEN**

Run:

```powershell
cd frontend
npm test -- memory-control-panel.behavior.test.tsx
```

Expected: memory panel behavior tests pass.

---

### Task 4: Admin Review Queue And Replay Candidate Export

**Files:**
- Modify: `frontend/src/components/MemoryControlPanel.tsx`
- Modify: `frontend/tests/memory-control-panel.behavior.test.tsx`
- Modify: `frontend/src/components/SettingsPage.css`

- [ ] **Step 1: Write failing lazy admin guard test**

On initial render, assert:

```ts
expect(apiMocks.getMemoryReviewCases).not.toHaveBeenCalled();
```

Click `Review Queue`, mock a 403 rejection, and assert the user memory list remains visible while the admin workspace shows `需要管理员权限`.

- [ ] **Step 2: Write failing admin list and decision test**

Mock one review case and assert status, feedback type, comment, snapshots, and replay id render. Submit `approve_replay` and assert:

```ts
expect(apiMocks.decideMemoryReviewCase).toHaveBeenCalledWith(201, expect.objectContaining({
  decision: 'approve_replay',
}));
```

Add a second assertion for `update_memory` with corrected content/type/confidence/comment.

- [ ] **Step 3: Write failing replay export test**

Click replay export and assert `exportMemoryReplayCandidates` is called and a JSON download is created.

- [ ] **Step 4: Verify RED**

Run:

```powershell
cd frontend
npm test -- memory-control-panel.behavior.test.tsx
```

Expected: fail because the admin workspace does not exist.

- [ ] **Step 5: Implement admin workspace**

Add workspace tabs, review status filter, admin lazy loader, 403 quiet state, decision controls, corrected-memory fields, safe snapshot rendering, and replay candidate export.

- [ ] **Step 6: Verify GREEN**

Run:

```powershell
cd frontend
npm test -- memory-control-panel.behavior.test.tsx
```

Expected: memory panel behavior tests pass.

---

### Task 5: Full Verification And Review

**Files:**
- All modified files.

- [ ] **Step 1: Backend targeted verification**

Run:

```powershell
cd backend
mvn "-Dtest=MemoryControlServiceTest,MemoryControllerTest,MemoryReviewServiceTest,MemoryReviewAdminControllerTest" test
```

Expected: all targeted backend tests pass.

- [ ] **Step 2: Frontend targeted verification**

Run:

```powershell
cd frontend
npm test -- memory-control-panel.behavior.test.tsx
```

Expected: all memory panel tests pass.

- [ ] **Step 3: Frontend build**

Run:

```powershell
cd frontend
npm run build
```

Expected: TypeScript and Vite build pass.

- [ ] **Step 4: Backend full test**

Run:

```powershell
cd backend
mvn test
```

Expected: full backend suite passes.

- [ ] **Step 5: Diff review**

Run:

```powershell
git diff --check
git diff -- backend/src/main/java/com/ainote/app/model/memory/MemoryResponse.java backend/src/main/java/com/ainote/app/service/MemoryControlService.java backend/src/test/java/com/ainote/app/service/MemoryControlServiceTest.java backend/src/test/java/com/ainote/app/controller/MemoryControllerTest.java frontend/src/api.ts frontend/src/components/MemoryControlPanel.tsx frontend/src/components/SettingsPage.css frontend/tests/memory-control-panel.behavior.test.tsx docs/superpowers/plans/2026-07-10-memory-explainability-ui.md docs/superpowers/specs/2026-07-09-memory-explainability-ui-design.md
```

Expected: no whitespace errors and the diff is limited to task 4 plus the approved task 4 plan/spec files.

---

## Self-Review

- Spec coverage: backend metadata exposure, user explanation, feedback, admin review queue, replay candidates, lazy admin loading, 403 behavior, safe JSON parsing, legacy fallback, and tests are mapped to tasks.
- Placeholder scan: no unresolved placeholders remain in executable steps.
- Type consistency: frontend payload and review case fields match the task 3 Java records; backend feedback and decision enum values remain exact strings from `MemoryReviewService`.
