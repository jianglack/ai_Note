# Memory Replay Evaluation Gates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a repeatable memory replay evaluation gate that scores capture decisions, should-not-remember safety, candidate extraction, expected policy reasons, and expected signals.

**Architecture:** Keep the current memory boundary intact: `MemoryCapturePolicy` decides allow/deny, `MemoryCandidateExtractor` turns allowed decisions into candidates, and the new evaluator only replays cases against those components. Add one small policy hardening for transient positive feedback so comments like "I like this answer" are not stored as long-term preferences.

**Tech Stack:** Spring Boot, Java records, Jackson test fixtures, JUnit 5, AssertJ, existing memory services.

---

## File Structure

- Create: `backend/src/main/java/com/ainote/app/service/MemoryReplayEvaluationService.java`
  - Responsibility: evaluate replay cases and report aggregate metrics plus per-case failures.
- Create: `backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java`
  - Responsibility: prove the evaluator catches false positives/false negatives and enforces L3 memory gates.
- Create: `backend/src/test/resources/memory/replay-eval-cases.json`
  - Responsibility: ASCII-only replay fixture for governed memory capture scenarios.
- Modify: `backend/src/main/java/com/ainote/app/service/MemorySignalClassifier.java`
  - Responsibility: classify transient assistant-feedback messages as deny-side signals.
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryCapturePolicy.java`
  - Responsibility: deny assistant feedback before allow-side preference rules.
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryCapturePolicyTest.java`
  - Responsibility: regression test for positive feedback false positives.

---

### Task 1: Add Replay Evaluator Contract

**Files:**
- Create: `backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java`
- Create later: `backend/src/main/java/com/ainote/app/service/MemoryReplayEvaluationService.java`

- [x] **Step 1: Write failing evaluator tests**

Add tests that:
- create inline replay cases;
- assert perfect reports produce `decisionAccuracy`, `shouldNotRememberPrecision`, `shouldRememberRecall`, and `candidateTypeAccuracy` of `1.0`;
- assert a deliberately wrong expected case appears in `failures`;
- load `replay-eval-cases.json` and enforce thresholds.

- [x] **Step 2: Run test to verify RED**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryReplayEvaluationServiceTest" test
```

Expected: compile failure because `MemoryReplayEvaluationService` does not exist.

- [x] **Step 3: Implement evaluator**

Create `MemoryReplayEvaluationService` with:
- `evaluate(List<MemoryReplayCase> cases)`;
- `MemoryReplayCase`;
- `EvaluationReport`;
- `CaseResult`;
- metrics for decision accuracy, should-not-remember precision, should-remember recall, candidate type accuracy, correction accuracy, reason coverage, and signal coverage.

- [x] **Step 4: Run test to verify GREEN or expose policy gaps**

Run:

```powershell
mvn "-Dtest=MemoryReplayEvaluationServiceTest" test
```

Expected after evaluator only: test may fail on transient feedback, proving a real policy gap.

---

### Task 2: Harden Positive Feedback Denial

**Files:**
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryCapturePolicyTest.java`
- Modify: `backend/src/main/java/com/ainote/app/service/MemorySignalClassifier.java`
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryCapturePolicy.java`

- [x] **Step 1: Write failing policy regression**

Add a test that evaluates:

```java
new MemoryCapturePolicy.CaptureRequest("user-1", "I like this answer, thanks.", "Glad it helped.")
```

Expected:
- `allowed=false`;
- `type=DENY_TRANSIENT`;
- `reason=assistant_feedback`;
- matched signals include `assistant_feedback`.

- [x] **Step 2: Run test to verify RED**

Run:

```powershell
mvn "-Dtest=MemoryCapturePolicyTest#positiveFeedbackAboutThisAnswerShouldNotBecomePreference" test
```

Expected: fail because current classifier treats `I like...` as a preference signal.

- [x] **Step 3: Implement classifier/policy hardening**

Add `assistantFeedback` to `SignalClassification`, detect feedback aimed at this answer/reply/response, include it in `hardDeny`, and make `MemoryCapturePolicy` return `DENY_TRANSIENT` with reason `assistant_feedback`.

- [x] **Step 4: Run focused tests**

Run:

```powershell
mvn "-Dtest=MemoryCapturePolicyTest,MemorySignalClassifierTest,MemoryReplayEvaluationServiceTest,MemorySystemEvaluationTest" test
```

Expected: all focused tests pass.

---

### Task 3: Verification And Commit

**Files:**
- All files from Tasks 1-2.

- [x] **Step 1: Run memory regression**

Run:

```powershell
mvn "-Dtest=MemoryCapturePolicyTest,MemorySignalClassifierTest,MemoryReplayEvaluationServiceTest,MemorySystemEvaluationTest,MemoryCandidateExtractorTest,MemoryWriteServiceTest,MemoryOrchestratorTest,MemoryExtractionServiceTest,MemoryRetrievalServiceTest,ContextAssemblerTest" test
```

Expected: 0 failures, 0 errors.

- [x] **Step 2: Run full backend tests**

Run:

```powershell
mvn test
```

Expected: build success.

- [x] **Step 3: Run diff hygiene**

Run:

```powershell
git diff --check
```

Expected: no whitespace errors.

- [ ] **Step 4: Commit exact files only**

Stage only:

```powershell
git add `
  docs/superpowers/plans/2026-07-05-memory-replay-evaluation-gates.md `
  backend/src/main/java/com/ainote/app/service/MemoryReplayEvaluationService.java `
  backend/src/main/java/com/ainote/app/service/MemorySignalClassifier.java `
  backend/src/main/java/com/ainote/app/service/MemoryCapturePolicy.java `
  backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java `
  backend/src/test/java/com/ainote/app/service/MemoryCapturePolicyTest.java `
  backend/src/test/resources/memory/replay-eval-cases.json
git commit -m "Add memory replay evaluation gates"
```

Expected: local commit created; unrelated untracked files remain untouched.
