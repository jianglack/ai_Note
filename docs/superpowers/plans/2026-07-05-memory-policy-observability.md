# Memory Policy Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist and display policy explanations for governed memory capture events.

**Architecture:** Keep `MemoryCapturePolicy` as the decision boundary and propagate its matched classifier signals through candidates into memory metadata and event snapshots. Add a small frontend parser that displays those signals in the memory event list.

**Tech Stack:** Spring Boot, Java records, JUnit 5, Mockito, React, TypeScript, existing memory governance services.

---

## File Structure

- Modify: `backend/src/main/java/com/ainote/app/service/MemoryCapturePolicy.java`
  - Add matched policy signals to `CaptureDecision`.
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryCandidateExtractor.java`
  - Propagate decision metadata into `MemoryCandidate`.
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryWriteService.java`
  - Persist policy explanation metadata and include it in event snapshots.
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryCapturePolicyTest.java`
  - Assert final decisions carry matched signals.
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryCandidateExtractorTest.java`
  - Assert candidates carry policy explanation fields.
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryWriteServiceTest.java`
  - Assert semantic memory metadata and event snapshot include policy signals.
- Modify: `frontend/src/components/MemoryControlPanel.tsx`
  - Parse event snapshots and render policy signal chips.
- Modify: `frontend/src/components/SettingsPage.css`
  - Style signal chips consistently with the memory panel.

---

### Task 1: Backend RED Tests

- [ ] Add a `MemoryCapturePolicyTest` assertion that stable style decisions contain `stable_style_preference`.
- [ ] Add a `MemoryCandidateExtractorTest` assertion that extracted style candidates contain `decisionType`, `policyReason`, and `policySignals`.
- [ ] Add a `MemoryWriteServiceTest` that writes a candidate with `policySignals` and expects both `metadata_json` and `after_json` to contain `policy_signals`.
- [ ] Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryCapturePolicyTest,MemoryCandidateExtractorTest,MemoryWriteServiceTest" test
```

Expected before implementation: tests fail because the new decision and candidate fields are missing.

### Task 2: Backend Implementation

- [ ] Add `matchedSignals` to `CaptureDecision` with defensive list copying.
- [ ] Make `MemoryCapturePolicy.evaluate(...)` pass classifier matched signals into every allow/deny decision.
- [ ] Extend `MemoryCandidate` with policy explanation fields while keeping the existing constructor for tests and callers.
- [ ] Include policy explanation data in `MemoryWriteService.applyGovernanceMetadata(...)`.
- [ ] Include `metadata_json` as a parsed JSON object in `MemoryWriteService.snapshot(...)`.
- [ ] Re-run the focused backend tests.

### Task 3: Frontend Event Display

- [ ] Add a safe JSON parser in `MemoryControlPanel.tsx`.
- [ ] Extract `metadata.policy_signals` from `event.afterJson`.
- [ ] Render the signals as compact chips above the raw event snapshot.
- [ ] Add CSS for `.memory-event-policy` and `.memory-signal-chip`.

### Task 4: Verification

- [ ] Run focused backend tests:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryCapturePolicyTest,MemoryCandidateExtractorTest,MemoryWriteServiceTest" test
```

- [ ] Run memory regression tests:

```powershell
mvn "-Dtest=MemoryCapturePolicyTest,MemorySignalClassifierTest,MemoryCandidateExtractorTest,MemorySystemEvaluationTest,MemoryWriteServiceTest,MemoryOrchestratorTest,MemoryExtractionServiceTest,MemoryRetrievalServiceTest,ContextAssemblerTest" test
```

- [ ] Run full backend tests:

```powershell
mvn test
```

- [ ] Run frontend checks:

```powershell
cd D:\ainotetest\frontend
npm test
npm run build
npm run lint
```

- [ ] Run whitespace check:

```powershell
cd D:\ainotetest
git diff --check
```

### Task 5: Runtime And Commit

- [ ] Restart the backend and verify `/actuator/health`.
- [ ] Commit only files from this plan.
- [ ] Report changed files, behavior, test results, risks, and manual verification steps.
