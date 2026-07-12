# Memory Human Review Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the production human review loop for memory failures, from user feedback through admin review and replay candidate export.

**Architecture:** Add a dedicated `memory_review_cases` workflow table and service. User APIs create pending review cases from user-owned memories; admin APIs review, action, and export approved replay candidates while writing `memory_events` audit rows.

**Tech Stack:** Java 17, Spring Boot, Spring MVC, Spring Data JPA, Flyway SQL migrations, JUnit 5, Mockito, AssertJ, MockMvc.

---

## File Structure

- Create `backend/src/main/resources/db/migration/V58__memory_review_cases.sql`: additive schema for review cases and indexes.
- Create `backend/src/main/java/com/ainote/app/entity/MemoryReviewCase.java`: JPA entity for review workflow state.
- Create `backend/src/main/java/com/ainote/app/repository/MemoryReviewCaseRepository.java`: query methods for queue and export.
- Create model records under `backend/src/main/java/com/ainote/app/model/memory/`:
  - `MemoryFeedbackRequest.java`
  - `MemoryReviewDecisionRequest.java`
  - `MemoryReviewCaseResponse.java`
  - `MemoryReviewCaseListResponse.java`
  - `MemoryReplayCandidateExportResponse.java`
- Modify `backend/src/main/java/com/ainote/app/controller/MemoryController.java`: add user feedback endpoint.
- Create `backend/src/main/java/com/ainote/app/controller/MemoryReviewAdminController.java`: admin review endpoints.
- Create `backend/src/main/java/com/ainote/app/service/MemoryReviewService.java`: business logic, state transitions, audit events, replay export.
- Modify `backend/src/test/java/com/ainote/app/service/MemoryMigrationSqlTest.java`: assert V58 schema.
- Create `backend/src/test/java/com/ainote/app/service/MemoryReviewServiceTest.java`: service-level TDD coverage.
- Modify `backend/src/test/java/com/ainote/app/controller/MemoryControllerTest.java`: user feedback route coverage.
- Create `backend/src/test/java/com/ainote/app/controller/MemoryReviewAdminControllerTest.java`: admin route and guard coverage.

## Task 1: Migration Contract

**Files:**
- Create: `backend/src/main/resources/db/migration/V58__memory_review_cases.sql`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryMigrationSqlTest.java`

- [ ] **Step 1: Write failing migration test**

Add a test that reads `V58__memory_review_cases.sql` and asserts the table, indexes, replay id uniqueness, and workflow columns exist.

- [ ] **Step 2: Run migration test to verify RED**

Run: `mvn -Dtest=MemoryMigrationSqlTest test`

Expected: FAIL because `V58__memory_review_cases.sql` does not exist yet.

- [ ] **Step 3: Add V58 migration**

Create the migration with `CREATE TABLE IF NOT EXISTS memory_review_cases`, workflow columns, timestamps, and indexes.

- [ ] **Step 4: Run migration test to verify GREEN**

Run: `mvn -Dtest=MemoryMigrationSqlTest test`

Expected: PASS.

## Task 2: Review Entity and Repository

**Files:**
- Create: `backend/src/main/java/com/ainote/app/entity/MemoryReviewCase.java`
- Create: `backend/src/main/java/com/ainote/app/repository/MemoryReviewCaseRepository.java`
- Create/modify tests in `backend/src/test/java/com/ainote/app/service/MemoryReviewServiceTest.java`

- [ ] **Step 1: Write failing service construction and query test**

Write tests that mock `MemoryReviewCaseRepository` and expect pending cases to be listed by status.

- [ ] **Step 2: Run test to verify RED**

Run: `mvn -Dtest=MemoryReviewServiceTest test`

Expected: compilation failure because entity/repository/service do not exist.

- [ ] **Step 3: Add entity and repository**

Implement fields matching the migration and repository methods:

- `findByStatusOrderByCreatedAtAsc(String status, Pageable pageable)`
- `findByStatusOrderByReviewedAtDesc(String status, Pageable pageable)`

- [ ] **Step 4: Run test to verify GREEN for repository-dependent service shell**

Run: `mvn -Dtest=MemoryReviewServiceTest test`

Expected: PASS for list behavior once service shell exists.

## Task 3: User Feedback Creation

**Files:**
- Create model records under `backend/src/main/java/com/ainote/app/model/memory/`
- Create/modify `backend/src/main/java/com/ainote/app/service/MemoryReviewService.java`
- Modify `backend/src/main/java/com/ainote/app/controller/MemoryController.java`
- Modify tests:
  - `backend/src/test/java/com/ainote/app/service/MemoryReviewServiceTest.java`
  - `backend/src/test/java/com/ainote/app/controller/MemoryControllerTest.java`

- [ ] **Step 1: Write failing service test**

Test that `createFeedback("user-1", 11L, request)`:

- loads memory through `findByIdAndUserId(11L, "user-1")`
- saves a `pending_review` case
- snapshots memory/source/policy fields
- records `FEEDBACK_REPORTED`
- returns a response with review case id, memory id, feedback type, and status

- [ ] **Step 2: Run service test to verify RED**

Run: `mvn -Dtest=MemoryReviewServiceTest test`

Expected: FAIL because `createFeedback` is missing.

- [ ] **Step 3: Implement minimal service behavior**

Add request normalization, ownership check, snapshot helpers, event creation, and response mapping.

- [ ] **Step 4: Run service test to verify GREEN**

Run: `mvn -Dtest=MemoryReviewServiceTest test`

Expected: PASS.

- [ ] **Step 5: Write failing controller test**

Test `POST /api/memories/11/feedback` uses `SecurityUtils.getCurrentUserId()` and delegates to `MemoryReviewService`.

- [ ] **Step 6: Run controller test to verify RED**

Run: `mvn -Dtest=MemoryControllerTest test`

Expected: FAIL because endpoint is missing.

- [ ] **Step 7: Implement controller endpoint**

Inject `MemoryReviewService` and add `@PostMapping("/{id}/feedback")`.

- [ ] **Step 8: Run controller test to verify GREEN**

Run: `mvn -Dtest=MemoryControllerTest test`

Expected: PASS.

## Task 4: Admin Review APIs and Decisions

**Files:**
- Create `backend/src/main/java/com/ainote/app/controller/MemoryReviewAdminController.java`
- Modify `backend/src/main/java/com/ainote/app/service/MemoryReviewService.java`
- Create `backend/src/test/java/com/ainote/app/controller/MemoryReviewAdminControllerTest.java`
- Extend `backend/src/test/java/com/ainote/app/service/MemoryReviewServiceTest.java`

- [ ] **Step 1: Write failing service tests for decisions**

Cover:

- `reject_feedback` sets status `rejected`, records `REVIEW_REJECTED`, and does not mutate memory.
- `disable_memory` sets memory status `disabled`, case status `actioned`, and records `REVIEW_DISABLED`.
- `delete_memory` sets memory status `deleted`, case status `actioned`, and records `REVIEW_DELETED`.
- `update_memory` patches content/type/confidence, case status `actioned`, and records `REVIEW_UPDATED`.
- `approve_replay` sets status `approved_for_replay`, generates replay fields, and records `REVIEW_APPROVED_FOR_REPLAY`.

- [ ] **Step 2: Run service tests to verify RED**

Run: `mvn -Dtest=MemoryReviewServiceTest test`

Expected: FAIL because decision handling is missing.

- [ ] **Step 3: Implement decision handling**

Normalize decisions, load review case, optionally load memory, mutate only for action decisions, save review case, and record audit events.

- [ ] **Step 4: Run service tests to verify GREEN**

Run: `mvn -Dtest=MemoryReviewServiceTest test`

Expected: PASS.

- [ ] **Step 5: Write failing admin controller tests**

Cover:

- list endpoint calls `AdminAccessGuard.checkAdminAccess()`
- decision endpoint calls guard and delegates with current reviewer id
- replay export endpoint calls guard and delegates

- [ ] **Step 6: Run admin controller tests to verify RED**

Run: `mvn -Dtest=MemoryReviewAdminControllerTest test`

Expected: FAIL because controller is missing.

- [ ] **Step 7: Implement admin controller**

Add routes under `/api/admin/memory-review-cases`.

- [ ] **Step 8: Run admin controller tests to verify GREEN**

Run: `mvn -Dtest=MemoryReviewAdminControllerTest test`

Expected: PASS.

## Task 5: Replay Candidate Export

**Files:**
- Modify `backend/src/main/java/com/ainote/app/service/MemoryReviewService.java`
- Create model record `MemoryReplayCandidateExportResponse.java`
- Extend `backend/src/test/java/com/ainote/app/service/MemoryReviewServiceTest.java`

- [ ] **Step 1: Write failing export test**

Test that export returns only `approved_for_replay` cases, caps limit to a safe range, and includes replay JSON and manifest JSON.

- [ ] **Step 2: Run export test to verify RED**

Run: `mvn -Dtest=MemoryReviewServiceTest test`

Expected: FAIL if export is absent or returns unapproved cases.

- [ ] **Step 3: Implement export**

Use repository query `findByStatusOrderByReviewedAtDesc("approved_for_replay", PageRequest.of(0, limit))`.

- [ ] **Step 4: Run export test to verify GREEN**

Run: `mvn -Dtest=MemoryReviewServiceTest test`

Expected: PASS.

## Task 6: Final Verification and Review

**Files:**
- All changed files.

- [ ] **Step 1: Run targeted backend tests**

Run:

```powershell
mvn -Dtest=MemoryMigrationSqlTest,MemoryReviewServiceTest,MemoryControllerTest,MemoryReviewAdminControllerTest test
```

Expected: PASS with 0 failures and 0 errors.

- [ ] **Step 2: Run full backend test suite**

Run:

```powershell
mvn test
```

Expected: PASS with 0 failures and 0 errors.

- [ ] **Step 3: Run whitespace diff check**

Run:

```powershell
git diff --check
```

Expected: exit code 0 or only pre-existing line-ending warnings already present in the repository.

- [ ] **Step 4: Review git diff**

Run:

```powershell
git diff -- backend/src/main/java/com/ainote/app backend/src/main/resources/db/migration backend/src/test/java/com/ainote/app docs/superpowers
```

Expected: diff only contains task 3 additions plus existing task 2 changes already in the worktree.

- [ ] **Step 5: Report status**

Report:

- design doc path
- plan doc path
- APIs added
- tests run and results
- known limitations deferred to task 4/5/6

## Plan Self-Review

Spec coverage:

- User feedback API: Task 3.
- Durable review queue: Tasks 1 and 2.
- Admin review state machine: Task 4.
- Replay candidate export: Task 5.
- Audit events: Tasks 3 and 4.
- Verification: Task 6.

Placeholder scan:

- No TBD/TODO placeholders.
- Each task has concrete files, commands, and expected outcomes.

Type consistency:

- `MemoryReviewCase`, `MemoryReviewCaseRepository`, `MemoryReviewService`, and API model names are consistent across tasks.
- Status and decision values match the design document.
