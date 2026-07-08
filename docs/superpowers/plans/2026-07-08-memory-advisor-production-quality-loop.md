# Memory Advisor Production Quality Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an offline production-readiness loop for memory advisor evaluation using the active replay dataset.

**Architecture:** Add a new service that wraps existing advisor and rule replay evaluators, measures latency, applies production thresholds, and emits a readiness report plus capped failure report. Keep the LLM advisor disabled by default and only add prompt version metadata for reproducibility.

**Tech Stack:** Java 17, Spring service, JUnit 5, AssertJ, Maven Surefire.

---

## Source Spec

Read before execution:

- `docs/superpowers/specs/2026-07-08-memory-advisor-production-quality-loop-design.md`

## File Responsibility Map

- `backend/src/main/java/com/ainote/app/service/MemoryAdvisorProductionQualityService.java`
  - New production-readiness service, threshold records, metrics records, gate failures, and failure report records.
- `backend/src/main/java/com/ainote/app/service/MemoryAdvisorReplayEvaluationService.java`
  - Add latency measurement support and p95 latency to advisor replay reports.
- `backend/src/main/java/com/ainote/app/service/LlmMemorySignalAdvisor.java`
  - Expose `PROMPT_VERSION` and include it in the advisor system prompt.
- `backend/src/test/java/com/ainote/app/service/MemoryAdvisorProductionQualityServiceTest.java`
  - New production quality gate tests.
- `backend/src/test/java/com/ainote/app/service/MemoryAdvisorReplayEvaluationServiceTest.java`
  - Extend existing tests to assert p95 latency is present.
- `backend/src/test/java/com/ainote/app/service/LlmMemorySignalAdvisorTest.java`
  - Extend prompt test to assert prompt version is present.

## Task 1: RED - Production Readiness Report

- [ ] **Step 1: Write failing test**

Create `MemoryAdvisorProductionQualityServiceTest` with a test that builds an oracle advisor from `MemoryReplayDatasetLoader.loadActiveDataset().cases()` and calls:

```java
MemoryAdvisorProductionQualityService service = new MemoryAdvisorProductionQualityService(
        new MemoryAdvisorReplayEvaluationService(),
        new MemoryReplayEvaluationService(new MemoryCapturePolicy(), new MemoryCandidateExtractor()));
MemoryAdvisorProductionQualityService.AdvisorReadinessReport report = service.evaluate(
        cases,
        oracleAdvisor,
        MemoryAdvisorProductionQualityService.AdvisorEvaluationRun.production(
                "advisor-prod-gate-2026-07-08",
                "llm-memory-advisor",
                "offline-oracle",
                LlmMemorySignalAdvisor.PROMPT_VERSION,
                "active-memory-replay-v2"));
```

Assert total cases are at least 4000, `report.qualityGatePassed()` is true, no gate failures exist, baseline accuracy is `1.0`, and the prompt/model/dataset versions are preserved.

- [ ] **Step 2: Run RED**

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorProductionQualityServiceTest" test
```

Expected: compile failure because `MemoryAdvisorProductionQualityService` does not exist.

## Task 2: GREEN - Readiness Service

- [ ] **Step 1: Implement service**

Create `MemoryAdvisorProductionQualityService` with:

```java
public AdvisorReadinessReport evaluate(
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
        MemorySignalAdvisor advisor,
        AdvisorEvaluationRun run)
```

The implementation must run advisor evaluation, run baseline evaluation, compute production metrics, evaluate thresholds, cap failure samples at 50, and return immutable records.

- [ ] **Step 2: Run GREEN**

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorProductionQualityServiceTest" test
```

Expected: production readiness oracle test passes.

## Task 3: RED/GREEN - Failure Gates

- [ ] **Step 1: Add failing degraded-advisor test**

Add a test where the advisor captures every case as `preference`. Assert readiness fails with gate names:

```java
false_positive_rate
sensitive_false_allow_rate
decision_accuracy_delta_vs_baseline
```

Also assert `report.failureReport().totalFailures()` is positive and `report.failureReport().samples()` is capped at 50.

- [ ] **Step 2: Implement missing gates**

Add any missing threshold checks and sensitive false-allow calculation.

- [ ] **Step 3: Run focused tests**

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorProductionQualityServiceTest" test
```

Expected: all production quality service tests pass.

## Task 4: RED/GREEN - Advisor Latency Metrics

- [ ] **Step 1: Extend advisor evaluator test**

Add an assertion that an advisor report exposes nonnegative `p95LatencyMillis()`.

- [ ] **Step 2: Implement latency measurement**

Measure each advisor call in `MemoryAdvisorReplayEvaluationService.evaluateCase` with `System.nanoTime()` and store milliseconds on each case result. Compute report p95 by sorting case latencies and selecting the ceiling percentile index.

- [ ] **Step 3: Run focused tests**

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorReplayEvaluationServiceTest,MemoryAdvisorProductionQualityServiceTest" test
```

Expected: both test classes pass.

## Task 5: RED/GREEN - Prompt Versioning

- [ ] **Step 1: Extend LLM advisor prompt test**

Assert the generated prompt contains `LlmMemorySignalAdvisor.PROMPT_VERSION`.

- [ ] **Step 2: Implement prompt version**

Add:

```java
static final String PROMPT_VERSION = "memory-advisor-v1";
```

Include the version in the system prompt returned by `systemPrompt()`.

- [ ] **Step 3: Run focused tests**

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=LlmMemorySignalAdvisorTest,MemoryAdvisorProductionQualityServiceTest,MemoryAdvisorReplayEvaluationServiceTest" test
```

Expected: all focused tests pass.

## Task 6: Verification And Commit

- [ ] **Step 1: Run complete backend tests**

```powershell
cd D:\ainotetest\backend
mvn test
```

Expected: build success, 0 failures, 0 errors.

- [ ] **Step 2: Review git diff**

```powershell
cd D:\ainotetest
git diff --check main..HEAD
git status --short --branch
```

Expected: no whitespace errors; only known unrelated untracked files remain.

- [ ] **Step 3: Commit implementation**

```powershell
cd D:\ainotetest
git add docs/superpowers/specs/2026-07-08-memory-advisor-production-quality-loop-design.md docs/superpowers/plans/2026-07-08-memory-advisor-production-quality-loop.md backend/src/main/java/com/ainote/app/service/MemoryAdvisorProductionQualityService.java backend/src/main/java/com/ainote/app/service/MemoryAdvisorReplayEvaluationService.java backend/src/main/java/com/ainote/app/service/LlmMemorySignalAdvisor.java backend/src/test/java/com/ainote/app/service/MemoryAdvisorProductionQualityServiceTest.java backend/src/test/java/com/ainote/app/service/MemoryAdvisorReplayEvaluationServiceTest.java backend/src/test/java/com/ainote/app/service/LlmMemorySignalAdvisorTest.java
git commit -m "test: add memory advisor production quality gate"
```
