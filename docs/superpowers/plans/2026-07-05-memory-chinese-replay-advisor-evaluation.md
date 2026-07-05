# Memory Chinese Replay Advisor Evaluation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand memory governance replay coverage with realistic Chinese cases and add advisor-specific quality metrics.

**Architecture:** Keep `MemoryReplayEvaluationService` as the policy/candidate evaluator. Add `MemoryAdvisorReplayEvaluationService` to score any `MemorySignalAdvisor` against replay cases without making real LLM calls in tests.

**Tech Stack:** Spring Boot, Java records, Jackson JSON fixtures, JUnit 5, AssertJ.

---

## Files

- Modify: `backend/src/test/resources/memory/replay-eval-cases.json`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java`
- Create: `backend/src/main/java/com/ainote/app/service/MemoryAdvisorReplayEvaluationService.java`
- Create: `backend/src/test/java/com/ainote/app/service/MemoryAdvisorReplayEvaluationServiceTest.java`
- Modify: `backend/src/main/java/com/ainote/app/service/MemorySignalClassifier.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryCapturePolicyTest.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemorySignalClassifierTest.java`

---

### Task 1: Expand Chinese Replay Dataset

- [x] **Step 1: Add failing replay dataset expectations**

Add assertions that the replay dataset has at least 24 cases and contains the Chinese case IDs for confirmation, style correction, project context, selected note, assistant feedback, and forget request.

- [x] **Step 2: Add Chinese cases to `replay-eval-cases.json`**

Add realistic Chinese capture and no-capture cases using existing expected fields.

- [x] **Step 3: Run replay tests**

Run:

```powershell
mvn "-Dtest=MemoryReplayEvaluationServiceTest" test
```

Expected before policy hardening: failures for common Chinese confirmation or assistant feedback if rules are missing.

### Task 2: Harden Chinese Boundary Signals

- [x] **Step 1: Add failing policy/classifier tests**

Add tests for:
- `可以` and `好的` as transient confirmations;
- `我喜欢这个回答，谢谢` as `assistant_feedback`.

- [x] **Step 2: Implement classifier rules**

Extend `isTransientOperation` and `isAssistantFeedback` with Chinese phrases only where the utterance clearly references the current answer/reply.

- [x] **Step 3: Run focused tests**

Run:

```powershell
mvn "-Dtest=MemoryCapturePolicyTest,MemorySignalClassifierTest,MemoryReplayEvaluationServiceTest" test
```

Expected: all pass.

### Task 3: Add Advisor Quality Evaluation

- [x] **Step 1: Write failing advisor evaluator tests**

Create tests that prove advisor metrics catch false positives, false negatives, unavailable results, and wrong memory type.

- [x] **Step 2: Implement `MemoryAdvisorReplayEvaluationService`**

Compute availability rate, capture decision accuracy, false positive rate, false negative rate, memory type accuracy, and case failure details.

- [x] **Step 3: Run advisor tests**

Run:

```powershell
mvn "-Dtest=MemoryAdvisorReplayEvaluationServiceTest" test
```

Expected: pass after implementation.

### Task 4: Verification And Commit

- [x] **Step 1: Run memory regression**

```powershell
mvn "-Dtest=MemoryCapturePolicyTest,MemorySignalClassifierTest,MemoryReplayEvaluationServiceTest,MemoryAdvisorReplayEvaluationServiceTest,MemorySystemEvaluationTest,MemoryCandidateExtractorTest,MemoryWriteServiceTest,MemoryOrchestratorTest,MemoryExtractionServiceTest,MemoryRetrievalServiceTest,ContextAssemblerTest" test
```

- [x] **Step 2: Run full backend tests**

```powershell
mvn test
```

- [x] **Step 3: Run diff hygiene**

```powershell
git diff --check
```

- [ ] **Step 4: Commit exact files**

Stage only this slice's files and commit:

```powershell
git commit -m "Expand Chinese memory replay advisor evaluation"
```
