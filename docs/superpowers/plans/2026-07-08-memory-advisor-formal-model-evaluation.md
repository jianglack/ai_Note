# Memory Advisor Formal Model Evaluation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a live, budget-gated formal evaluation path for the real LLM memory advisor.

**Architecture:** Main code provides reusable budget preflight, run execution, and JSON report writing. Test/evaluation code provides the opt-in integration entry point that loads replay data and constructs the real DeepSeek-compatible `ChatModel` from environment variables.

**Tech Stack:** Java 17, JUnit 5, AssertJ, Jackson, LangChain4j OpenAiChatModel, Maven Surefire.

---

## File Responsibility Map

- `backend/src/main/java/com/ainote/app/service/MemoryAdvisorFormalEvaluationService.java`
  - Create. Budget estimate, preflight blocking, production quality invocation, and report writing.
- `backend/src/test/java/com/ainote/app/service/MemoryAdvisorFormalEvaluationServiceTest.java`
  - Create. Offline unit tests for budget blocking, no-call behavior, report writing, and successful fake-advisor run.
- `backend/src/test/java/com/ainote/app/service/MemoryAdvisorFormalEvaluationIT.java`
  - Create. Opt-in live integration test using the active replay dataset and real model.
- `backend/src/test/java/com/ainote/app/config/MemoryPropertiesTest.java`
  - No change required unless formal evaluation config is added to `MemoryProperties`; current design uses a request record instead.
- `backend/src/main/resources/application.yml.example`
  - Modify to document formal evaluation environment variables.
- `docs/superpowers/specs/2026-07-08-memory-advisor-formal-model-evaluation-design.md`
  - Create. Design spec.
- `docs/superpowers/plans/2026-07-08-memory-advisor-formal-model-evaluation.md`
  - Create. Implementation plan.

## Task 1: RED - Budget Blocks Unsafe Runs

- [ ] **Step 1: Write failing unit tests**

Create `MemoryAdvisorFormalEvaluationServiceTest` with:

```java
@Test
void preflightBlocksDisabledLiveRunBeforeAdvisorIsCalled() {
    AtomicInteger calls = new AtomicInteger();
    MemorySignalAdvisor advisor = request -> {
        calls.incrementAndGet();
        return MemorySignalAdvisor.AdvisorResult.noCapture("none", 0.1, List.of(), "not memory");
    };

    MemoryAdvisorFormalEvaluationService.FormalEvaluationReport report = service.run(
            List.of(replayCase("case-1", "remember: concise", true)),
            advisor,
            request(false, 10.0, 100000));

    assertThat(report.status()).isEqualTo(MemoryAdvisorFormalEvaluationService.RunStatus.BLOCKED);
    assertThat(report.blockReasons()).contains("formal_evaluation_disabled");
    assertThat(calls).hasValue(0);
}
```

Add another test where estimated cost exceeds the cap and assert `estimated_cost_exceeds_budget`.

- [ ] **Step 2: Run RED**

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorFormalEvaluationServiceTest" test
```

Expected: compile failure because `MemoryAdvisorFormalEvaluationService` does not exist.

## Task 2: GREEN - Formal Evaluation Service

- [ ] **Step 1: Implement service**

Create records:

- `FormalEvaluationRequest`
- `FormalEvaluationBudget`
- `FormalEvaluationReport`
- `RunStatus`

Implement:

```java
public FormalEvaluationReport run(
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
        MemorySignalAdvisor advisor,
        FormalEvaluationRequest request)
```

The method computes budget, writes reports for blocked and completed runs, and only calls the advisor when preflight passes.

- [ ] **Step 2: Run GREEN**

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorFormalEvaluationServiceTest" test
```

Expected: tests pass.

## Task 3: RED/GREEN - Successful Fake Advisor Run

- [ ] **Step 1: Add success test**

Use an oracle advisor over a small case list and assert:

- `status=COMPLETED`;
- `readinessReport` is not null;
- report file exists;
- report JSON does not contain `DEEPSEEK_API_KEY`.

- [ ] **Step 2: Implement missing report writing**

Write JSON to `${reportDir}/${runId}.json` with Jackson.

- [ ] **Step 3: Run focused tests**

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorFormalEvaluationServiceTest" test
```

Expected: pass.

## Task 4: Live Integration Entry

- [ ] **Step 1: Add opt-in integration test**

Create `MemoryAdvisorFormalEvaluationIT` with:

- `@EnabledIfEnvironmentVariable(named = "MEMORY_ADVISOR_FORMAL_EVAL_ENABLED", matches = "true")`;
- active replay dataset loading via `MemoryReplayDatasetLoader`;
- real `OpenAiChatModel` built from `DEEPSEEK_API_KEY`, `DEEPSEEK_MODEL`, and `DEEPSEEK_BASE_URL`;
- `MemoryProperties` with advisor enabled;
- `LlmMemorySignalAdvisor`;
- `MemoryAdvisorFormalEvaluationService.run(...)`.

Assert completed runs have a report file and blocked runs include explicit reasons.

- [ ] **Step 2: Run disabled-path check**

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorFormalEvaluationIT" test
```

Expected: test is skipped when the enable flag is absent.

## Task 5: Docs And Verification

- [ ] **Step 1: Document env vars**

Update `backend/src/main/resources/application.yml.example` with the formal eval env variables.

- [ ] **Step 2: Run focused tests**

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorFormalEvaluationServiceTest,MemoryAdvisorProductionQualityServiceTest,MemoryAdvisorFormalEvaluationIT" test
```

Expected: service tests pass; IT skipped unless enabled.

- [ ] **Step 3: Run full backend tests**

```powershell
cd D:\ainotetest\backend
mvn test
```

Expected: build success, 0 failures, 0 errors.

- [ ] **Step 4: Commit**

```powershell
cd D:\ainotetest
git add docs/superpowers/specs/2026-07-08-memory-advisor-formal-model-evaluation-design.md docs/superpowers/plans/2026-07-08-memory-advisor-formal-model-evaluation.md backend/src/main/java/com/ainote/app/service/MemoryAdvisorFormalEvaluationService.java backend/src/test/java/com/ainote/app/service/MemoryAdvisorFormalEvaluationServiceTest.java backend/src/test/java/com/ainote/app/service/MemoryAdvisorFormalEvaluationIT.java backend/src/main/resources/application.yml.example
git commit -m "test: add budgeted memory advisor formal evaluation"
```
