# Memory Production Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the existing L3 governed memory system one step closer to production-grade operation by strengthening provenance, auditability, failure observability, and verification without redesigning the memory architecture.

**Architecture:** Keep the current Spring Boot memory boundary: `MemoryOrchestrator` decides when capture runs, `MemoryCapturePolicy` decides whether a turn is memory-worthy, `MemoryWriteService` owns durable semantic memory writes and event ledger writes, `MemoryRetrievalService` owns query-relevant recall, and `ContextAssembler` owns context injection. This plan only hardens the current boundary; it does not create a new memory subsystem.

**Tech Stack:** Spring Boot, JPA, PostgreSQL, pgvector, existing Flyway migrations, LangChain4j, JUnit 5, Mockito, AssertJ, React memory panel already present.

---

## Review Findings

The previous plan was too narrow and not sufficiently execution-grade. It identified a useful first hardening slice, but it missed several things a reviewer needs before approving execution:

- It did not state the target maturity level and what this slice does not solve.
- It did not provide a phase order with clear stop points.
- It did not define acceptance criteria separate from implementation steps.
- It did not include a risk and rollback section.
- It did not map current dirty worktree changes to the plan.
- It did not mention compatibility with existing `V56__memory_governance_schema.sql`.
- It did not separate production behavior, tests, and future enterprise-hardening roadmap.

This revised plan fixes those gaps.

## Current Baseline

The project already has these L3 memory components:

- `backend/src/main/java/com/ainote/app/service/MemoryOrchestrator.java`
- `backend/src/main/java/com/ainote/app/service/MemoryCapturePolicy.java`
- `backend/src/main/java/com/ainote/app/service/MemoryCandidateExtractor.java`
- `backend/src/main/java/com/ainote/app/service/MemoryWriteService.java`
- `backend/src/main/java/com/ainote/app/service/MemoryRetrievalService.java`
- `backend/src/main/java/com/ainote/app/service/ContextAssembler.java`
- `backend/src/main/java/com/ainote/app/controller/MemoryController.java`
- `frontend/src/components/MemoryControlPanel.tsx`
- `backend/src/main/resources/db/migration/V56__memory_governance_schema.sql`

The relevant defaults are already production-facing:

- `MEMORY_ORCHESTRATOR_ENABLED:true`
- `MEMORY_CAPTURE_MODE:policy`
- `MEMORY_RETRIEVAL_MODE:query_relevant`
- `MEMORY_CHAT_HISTORY_WRITE_MODE:append`

The gap this plan addresses: governance fields exist in the schema but are not consistently populated, and async capture failures are visible in logs but not consistently represented in the durable memory event ledger.

## Current Worktree State

At the time this plan was revised, these uncommitted draft files existed:

- `backend/src/main/java/com/ainote/app/service/MemoryWriteService.java`
- `backend/src/test/java/com/ainote/app/service/MemoryWriteServiceTest.java`
- `backend/src/test/java/com/ainote/app/service/MemoryOrchestratorTest.java`
- `docs/superpowers/plans/2026-07-05-memory-production-hardening.md`

There are also pre-existing untracked docs under `docs/superpowers/...` and `.superpowers/`. Do not delete or overwrite them.

Before execution resumes, run:

```powershell
cd D:\ainotetest
git status --short
git diff -- backend/src/main/java/com/ainote/app/service/MemoryWriteService.java backend/src/test/java/com/ainote/app/service/MemoryWriteServiceTest.java backend/src/test/java/com/ainote/app/service/MemoryOrchestratorTest.java
```

Expected:

- The three memory-hardening draft files are modified.
- This plan file is untracked or modified.
- Existing unrelated untracked docs remain untouched.

Do not commit any draft implementation until the revised plan is approved and the verification commands in this plan pass.

## Scope

### In Scope

- Populate semantic-memory provenance fields during governed writes:
  - `contentHash`
  - `sourceTraceId`
  - `sourceMessageIds`
  - `metadataJson`
- Set `MemoryEvent.traceId` for memory lifecycle events:
  - `CREATED`
  - `REINFORCED`
  - `SUPERSEDED`
- Add durable event-ledger entry for async capture failures:
  - `CAPTURE_FAILED`
- Keep existing feature flags and defaults.
- Add tests before finalizing implementation.
- Run focused and memory-regression tests.

### Out Of Scope

- No new UI.
- No database migration unless local schema inspection proves a required column is missing.
- No encryption, retention policy, or PII redaction engine in this slice.
- No rewrite of `MemoryExtractionService`.
- No change to `MemoryCapturePolicy` decision semantics.
- No change to query-relevant retrieval ranking.
- No admin audit dashboard.
- No full enterprise compliance package.

## Acceptance Criteria

This slice is complete only when all criteria are true:

- New semantic memories have a stable `contentHash` in the format `sha256:<64 lowercase hex chars>`.
- New semantic memories have a deterministic `sourceTraceId` in the format `memory-capture-<24 lowercase hex chars>`.
- New semantic memories have `sourceMessageIds` containing at least a hashed user evidence marker.
- New semantic memories have `metadataJson` containing at least capture reason and write boundary source.
- `CREATED`, `REINFORCED`, and `SUPERSEDED` events carry a trace id.
- If `MemoryOrchestrator.captureAfterTurn(...)` throws during extraction or write, a `CAPTURE_FAILED` ledger event is persisted through `MemoryWriteService`.
- Existing memory governance tests still pass.
- Full backend test suite passes, or any failure is documented as unrelated and not bypassed.
- The local commit contains only planned files.

## File Responsibility Map

- `MemoryWriteService.java`
  - Single write boundary for semantic memory persistence and memory ledger events.
  - Owns provenance construction for governed memory writes.
  - Owns failure ledger entry creation.
- `MemoryOrchestrator.java`
  - Owns async capture orchestration and failure handoff.
  - Does not directly write repositories.
- `MemoryWriteServiceTest.java`
  - Verifies semantic memory provenance and ledger event fields.
  - Verifies capture failure ledger event creation.
- `MemoryOrchestratorTest.java`
  - Verifies orchestrator delegates capture failure recording to `MemoryWriteService`.

## Risks

- Deterministic trace IDs may expose stable correlation if built from raw text. Use SHA-256 and store only hashes, not raw user messages.
- Hand-built JSON strings can become fragile. Keep values simple in this slice; use `ObjectMapper` in a later cleanup if metadata grows.
- Failure ledger writes inside catch blocks must not throw outward and break user-facing chat flow. If `recordCaptureFailure(...)` throws, `MemoryOrchestrator` should log but not rethrow.
- Existing tests use mocks, so repository-level persistence of new fields should be covered later with an integration test if this becomes a compliance requirement.

## Rollback Plan

Rollback is safe because this plan is additive and uses existing nullable columns.

To roll back after commit:

```powershell
cd D:\ainotetest
git revert <commit-sha>
```

No schema rollback is needed.

If execution is paused before commit:

```powershell
git diff -- backend/src/main/java/com/ainote/app/service/MemoryWriteService.java backend/src/main/java/com/ainote/app/service/MemoryOrchestrator.java backend/src/test/java/com/ainote/app/service/MemoryWriteServiceTest.java backend/src/test/java/com/ainote/app/service/MemoryOrchestratorTest.java
```

Do not discard changes unless the user explicitly requests it.

---

### Task 0: Baseline Audit

**Files:**
- Read: `backend/src/main/java/com/ainote/app/service/MemoryWriteService.java`
- Read: `backend/src/main/java/com/ainote/app/service/MemoryOrchestrator.java`
- Read: `backend/src/test/java/com/ainote/app/service/MemoryWriteServiceTest.java`
- Read: `backend/src/test/java/com/ainote/app/service/MemoryOrchestratorTest.java`
- Read: `backend/src/main/resources/db/migration/V56__memory_governance_schema.sql`

- [ ] **Step 1: Check dirty state**

Run:

```powershell
cd D:\ainotetest
git status --short
```

Expected: dirty files match the Current Worktree State section.

- [ ] **Step 2: Confirm schema fields already exist**

Run:

```powershell
rg -n "source_trace_id|source_message_ids|content_hash|metadata_json|trace_id" backend/src/main/resources/db/migration/V56__memory_governance_schema.sql
```

Expected: all five fields are found.

- [ ] **Step 3: Confirm existing red test evidence**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryWriteServiceTest,MemoryOrchestratorTest" test
```

Expected before implementation is complete: fail because `recordCaptureFailure(...)` or new provenance behavior is missing. If the current draft already compiles, inspect whether implementation was partially applied before this plan revision.

---

### Task 1: Semantic Memory Provenance

**Files:**
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryWriteServiceTest.java`
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryWriteService.java`

- [ ] **Step 1: Write or confirm failing test**

In `MemoryWriteServiceTest.writesNewExplicitPreferenceWithProvenanceAndEvent`, require the saved memory to include provenance:

```java
assertThat(saved.getContentHash()).matches("sha256:[a-f0-9]{64}");
assertThat(saved.getSourceTraceId()).matches("memory-capture-[a-f0-9]{24}");
assertThat(saved.getSourceMessageIds()).contains("user_message_hash", "sha256:");
assertThat(saved.getMetadataJson()).contains("capture_reason", "explicit");
```

Require the event to carry the same trace id:

```java
assertThat(eventCaptor.getValue().getTraceId()).isEqualTo(saved.getSourceTraceId());
assertThat(eventCaptor.getValue().getAfterJson()).contains("contentHash");
```

- [ ] **Step 2: Run test to verify red**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryWriteServiceTest" test
```

Expected: fail if provenance code is absent.

- [ ] **Step 3: Implement deterministic provenance helpers**

In `MemoryWriteService.java`, add imports:

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
```

Add helpers:

```java
private String traceIdFor(String userId, MemoryCandidateExtractor.MemoryCandidate candidate) {
    return "memory-capture-" + sha256(safe(userId)
            + "\u001f" + safe(candidate.evidenceExcerpt())
            + "\u001f" + safe(candidate.content())).substring(0, 24);
}

private String sha256(String value) {
    try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(safe(value).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("SHA-256 not available", e);
    }
}

private String jsonEscape(String value) {
    return safe(value)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
}
```

- [ ] **Step 4: Apply provenance before save**

Add:

```java
private void applyGovernanceMetadata(SemanticMemory memory,
                                     MemoryCandidateExtractor.MemoryCandidate candidate,
                                     String reason) {
    memory.setContentHash("sha256:" + sha256(candidate.content()));
    memory.setSourceTraceId(traceIdFor(memory.getUserId(), candidate));
    memory.setSourceMessageIds("{\"user_message_hash\":\"sha256:" + sha256(candidate.evidenceExcerpt()) + "\"}");
    memory.setMetadataJson("{\"capture_reason\":\"" + jsonEscape(reason)
            + "\",\"policy_source\":\"memory_write_service\"}");
}
```

Call it in:

- exact-match reinforce path before `semanticMemoryRepository.save(memory)`
- new memory path before `semanticMemoryRepository.save(memory)`

- [ ] **Step 5: Pass trace id into events**

Change `recordEvent(...)` signature to:

```java
private void recordEvent(String userId,
                         Long memoryId,
                         String eventType,
                         String actor,
                         String reason,
                         String beforeJson,
                         String afterJson,
                         String traceId)
```

Inside it:

```java
event.setTraceId(traceId);
```

Update `CREATED`, `REINFORCED`, and `SUPERSEDED` call sites.

- [ ] **Step 6: Run focused test**

Run:

```powershell
mvn "-Dtest=MemoryWriteServiceTest" test
```

Expected: `MemoryWriteServiceTest` passes or only fails on Task 2 behavior if Task 2 test has already been added.

---

### Task 2: Capture Failure Ledger

**Files:**
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryWriteServiceTest.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryOrchestratorTest.java`
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryWriteService.java`
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryOrchestrator.java`

- [ ] **Step 1: Write or confirm failure event test**

Add to `MemoryWriteServiceTest`:

```java
@Test
void recordsCaptureFailureAsLedgerEvent() {
    service.recordCaptureFailure(
            "user-1",
            "capture_exception",
            new IllegalStateException("extractor down"),
            "memory-capture-deadbeefdeadbeefdeadbeef");

    ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
    verify(memoryEventRepository).save(eventCaptor.capture());
    MemoryEvent event = eventCaptor.getValue();
    assertThat(event.getEventType()).isEqualTo("CAPTURE_FAILED");
    assertThat(event.getActor()).isEqualTo("system");
    assertThat(event.getReason()).isEqualTo("capture_exception");
    assertThat(event.getTraceId()).isEqualTo("memory-capture-deadbeefdeadbeefdeadbeef");
    assertThat(event.getAfterJson()).contains("IllegalStateException", "extractor down");
}
```

- [ ] **Step 2: Write or confirm orchestrator delegation test**

In `MemoryOrchestratorTest.extractionFailureIsLoggedAsObservableCaptureFailure`, add:

```java
verify(writeService).recordCaptureFailure(
        eq("user-1"),
        eq("capture_exception"),
        any(IllegalStateException.class),
        any());
```

- [ ] **Step 3: Run tests to verify red**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryWriteServiceTest,MemoryOrchestratorTest" test
```

Expected: fail if `recordCaptureFailure(...)` or orchestrator delegation is absent.

- [ ] **Step 4: Implement failure event writer**

Add to `MemoryWriteService`:

```java
public void recordCaptureFailure(String userId, String reason, Throwable error, String traceId) {
    if (userId == null || userId.isBlank()) {
        return;
    }
    String resolvedTraceId = traceId == null || traceId.isBlank()
            ? "memory-capture-" + sha256(userId + "\u001f" + safe(reason) + "\u001f" + errorClass(error))
                    .substring(0, 24)
            : traceId;
    MemoryEvent event = new MemoryEvent();
    event.setUserId(userId);
    event.setEventType("CAPTURE_FAILED");
    event.setActor("system");
    event.setReason(reason);
    event.setTraceId(resolvedTraceId);
    event.setAfterJson("{\"error_class\":\"" + jsonEscape(errorClass(error))
            + "\",\"message\":\"" + jsonEscape(errorMessage(error)) + "\"}");
    memoryEventRepository.save(event);
}

private String errorClass(Throwable error) {
    return error == null ? "Unknown" : error.getClass().getSimpleName();
}

private String errorMessage(Throwable error) {
    return error == null || error.getMessage() == null ? "" : error.getMessage();
}
```

- [ ] **Step 5: Call failure writer from orchestrator**

In `MemoryOrchestrator.captureAfterTurn(...)`, inside `catch (Exception e)`:

```java
String traceId = "memory-capture-" + shortHash(userId, userMessage, aiResponse);
try {
    writeService.recordCaptureFailure(userId, "capture_exception", e, traceId);
} catch (Exception ledgerError) {
    log.warn("memory_capture_event=failure_ledger_write_failed user_id={} error={}",
            userId, ledgerError.getClass().getSimpleName(), ledgerError);
}
```

Add local helpers in `MemoryOrchestrator`:

```java
private String shortHash(String userId, String userMessage, String aiResponse) {
    return sha256(nullSafe(userId) + "\u001f" + nullSafe(userMessage) + "\u001f" + nullSafe(aiResponse))
            .substring(0, 24);
}

private String nullSafe(String value) {
    return value == null ? "" : value;
}
```

If this introduces duplicate SHA-256 code, keep duplication for this slice to avoid creating a shared utility before a second caller justifies it.

- [ ] **Step 6: Run focused tests**

Run:

```powershell
mvn "-Dtest=MemoryWriteServiceTest,MemoryOrchestratorTest" test
```

Expected: both tests pass.

---

### Task 3: Memory Regression Suite

**Files:**
- No new production files.

- [ ] **Step 1: Run focused memory suite**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryCapturePolicyTest,MemoryCandidateExtractorTest,MemoryWriteServiceTest,MemoryRetrievalServiceTest,MemoryControlServiceTest,MemoryControllerTest,MemorySystemEvaluationTest,ContextAssemblerTest,ReliableChatMemoryStoreFlushTest,MemoryOrchestratorTest" test
```

Expected:

- Build success.
- All selected memory tests pass.

- [ ] **Step 2: Run full backend suite**

Run:

```powershell
mvn test
```

Expected:

- Build success.
- If a failure is unrelated to memory hardening, capture the exact failing test and reason. Do not bypass tests.

- [ ] **Step 3: Optional frontend smoke if backend changes affect memory API responses**

Only run this if `MemoryController`, `MemoryControlService`, response models, or frontend memory panel are changed:

```powershell
cd D:\ainotetest\frontend
npm test
npm run build
npm run lint
```

Expected:

- Tests and build pass.
- Lint has no errors. Existing warnings should be reported but not fixed in this slice unless caused by this work.

---

### Task 4: Commit and Report

**Files:**
- Commit only planned memory-hardening files.

- [ ] **Step 1: Check diff**

Run:

```powershell
cd D:\ainotetest
git diff --check
git status --short
```

Expected:

- No whitespace errors.
- Only planned tracked files are modified.
- Pre-existing untracked docs are still untracked.

- [ ] **Step 2: Commit**

Run:

```powershell
git add backend/src/main/java/com/ainote/app/service/MemoryWriteService.java backend/src/main/java/com/ainote/app/service/MemoryOrchestrator.java backend/src/test/java/com/ainote/app/service/MemoryWriteServiceTest.java backend/src/test/java/com/ainote/app/service/MemoryOrchestratorTest.java docs/superpowers/plans/2026-07-05-memory-production-hardening.md
git commit -m "Harden memory provenance and failure ledger"
```

Expected:

- One local commit.
- No unrelated files staged.

- [ ] **Step 3: Report**

Report:

- modified files
- implemented behavior
- tests run and results
- remaining risks
- next recommended hardening slice

## Next Hardening Slices After This Plan

These are not part of this plan, but they are the logical enterprise-readiness path:

1. Retention and expiration policy for long-term memories.
2. PII detection and redaction before memory write.
3. Repository or integration test proving provenance columns persist through PostgreSQL.
4. Admin/audit view for memory event ledger.
5. Metrics for capture allow/deny/fail, user deletion rate, retrieval hit rate, and memory injection count.
6. Encryption-at-rest or application-level encryption for sensitive memory metadata.
7. Larger eval dataset with false-positive and false-negative scoring.

## Self-Review

- Spec coverage: covers provenance and failure-ledger hardening only.
- Scope check: focused enough for one implementation slice.
- Placeholder scan: no TBD, TODO, or “implement later” placeholders.
- Type consistency: planned methods use existing `SemanticMemory`, `MemoryEvent`, `MemoryWriteService`, and `MemoryOrchestrator`.
- Rollback: additive, no schema rollback required.
