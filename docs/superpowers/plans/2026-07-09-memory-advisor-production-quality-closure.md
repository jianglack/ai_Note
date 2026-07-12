# Memory Advisor Production Quality Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete task 2 by making the memory advisor evaluation loop operational, auditable, calibrated, comparable against rules, and gated before release.

**Architecture:** Keep production capture conservative: deterministic memory policy remains the final safety boundary, while LLM advisor raw output is retained for calibration and A/B diagnostics. Add test-only formal-evaluation case selection so paid model runs can use exact regression sets or stratified subsets before a full 4000-case release run.

**Tech Stack:** Java 17, JUnit 5, Maven, Spring service classes, JSONL formal-evaluation reports.

---

### Task 1: Clean Diff And Preserve Dataset Semantics

**Files:**
- Modify: `backend/src/test/resources/memory/external-real-human-replay-dataset.json`
- Verify: `git diff --numstat`

- [x] **Step 1: Compare parsed external dataset against HEAD**

Run:

```powershell
node -e "<parse HEAD and worktree JSON, compare cases and manifest>"
```

Expected: `caseDiffCount=0`, `manifestDiff=0`.

- [x] **Step 2: Remove formatting-only diff**

Run:

```powershell
git restore --worktree -- backend/src/test/resources/memory/external-real-human-replay-dataset.json
```

Expected: `external-real-human-replay-dataset.json` no longer appears in `git status`.

### Task 2: Deterministic Formal Evaluation Selection

**Files:**
- Create: `backend/src/test/java/com/ainote/app/service/MemoryAdvisorFormalEvaluationCaseSelector.java`
- Create: `backend/src/test/java/com/ainote/app/service/MemoryAdvisorFormalEvaluationCaseSelectorTest.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryAdvisorFormalEvaluationIT.java`

- [x] **Step 1: Write failing tests**

Tests must prove:
- exact case-id selection preserves requested order and fails on missing ids;
- stratified selection includes seed, simulated, and external cases when available;
- stratified selection is deterministic and respects `maxCases`.

- [x] **Step 2: Run selector tests and verify RED**

Run:

```powershell
mvn "-Dtest=MemoryAdvisorFormalEvaluationCaseSelectorTest" test
```

Expected: compilation/test failure because selector does not exist.

- [x] **Step 3: Implement selector**

Implement a package-private selector with `SelectionConfig(strategy, caseIds, maxCases)`, supporting `FIRST`, `EXACT_IDS`, and `STRATIFIED`.

- [x] **Step 4: Wire selector into formal IT**

Environment variables:
- `MEMORY_ADVISOR_FORMAL_EVAL_CASE_IDS`
- `MEMORY_ADVISOR_FORMAL_EVAL_SELECTION_STRATEGY`

When case ids or stratified selection are used, default min/max cases to the selected count unless explicitly overridden.

- [x] **Step 5: Run selector and formal-evaluation tests**

Run:

```powershell
mvn "-Dtest=MemoryAdvisorFormalEvaluationCaseSelectorTest,MemoryAdvisorFormalEvaluationServiceTest,MemoryAdvisorFormalBatchEvaluationServiceTest" test
```

Expected: all pass.

### Task 3: Targeted Failure Regression

**Files:**
- Create: `backend/src/test/resources/memory/advisor-v2-fix1-failure-case-ids.txt`
- Test via: `MemoryAdvisorFormalEvaluationIT`

- [x] **Step 1: Add old 34 failure ids**

The file must contain the old `fix1` failure ids, one per line, with comments allowed.

- [x] **Step 2: Run local non-API selector validation**

Run:

```powershell
mvn "-Dtest=MemoryAdvisorFormalEvaluationCaseSelectorTest" test
```

Expected: selected ids resolve to active dataset cases.

- [x] **Step 3: Run paid targeted regression**

Run `MemoryAdvisorFormalEvaluationIT` with `MEMORY_ADVISOR_FORMAL_EVAL_CASE_IDS` set from the file, `MIN_CASES=34`, `MAX_CASES=34`, and a unique run id.

Expected release-readiness report should have no decision/type failures; any API unavailable cases are retried once before classification.

### Task 4: Stratified Paid Evaluation

**Files:**
- Test via: `MemoryAdvisorFormalEvaluationIT`

- [x] **Step 1: Run 500-case stratified paid eval**

Set:

```text
MEMORY_ADVISOR_FORMAL_EVAL_SELECTION_STRATEGY=stratified
MEMORY_ADVISOR_FORMAL_EVAL_MAX_CASES=500
MEMORY_ADVISOR_FORMAL_EVAL_MIN_CASES=500
```

Expected: report includes seed, simulated, and external cases; quality gate passes except any explicitly documented provider-latency SLO block.

- [x] **Step 2: Analyze failures**

Any failure must be categorized as data taxonomy, test harness, production rule, prompt/model, or provider availability/latency before changing code.

### Task 5: Final Release Evidence

**Files:**
- Generated reports under `backend/target/memory-advisor-formal-eval/`

- [x] **Step 1: Run full 4000-case paid eval only after Tasks 3 and 4 pass**

Expected: `qualityGatePassed=true` and release gate `PASS`, or a documented `BLOCKED` reason limited to external provider latency/SLO.

- [x] **Step 2: Final local verification**

Run:

```powershell
mvn test
git diff --check
```

Expected: Maven success; no diff-check errors.

### Execution Evidence

- 34-case targeted regression:
  - Report: `backend/target/memory-advisor-formal-eval/memory-advisor-v2-formal-eval-fix2-targeted-34.batch.json`
  - Result: `evaluatedCases=34`, `availabilityRate=1.0`, `captureDecisionAccuracy=1.0`, `memoryTypeAccuracy=1.0`, `failureCount=0`.
- 500-case stratified paid evaluation:
  - Report: `backend/target/memory-advisor-formal-eval/memory-advisor-v2-formal-eval-fix2-stratified-500.batch.json`
  - Result: `evaluatedCases=500`, `availabilityRate=1.0`, `captureDecisionAccuracy=1.0`, `memoryTypeAccuracy=1.0`, `failureCount=0`.
- 4000-case full paid release evaluation:
  - Report: `backend/target/memory-advisor-formal-eval/memory-advisor-v2-formal-eval-fix2-release-4000.batch.json`
  - Result: `releaseGateStatus=PASS`, `qualityGatePassed=true`, `evaluatedCases=4000`, `availabilityRate=1.0`, `captureDecisionAccuracy=1.0`, `falsePositiveRate=0.0`, `falseNegativeRate=0.0`, `memoryTypeAccuracy=1.0`, `failureCount=0`, `failureMetricsP95LatencyMillis=1936`.
- Local verification:
  - Targeted suite: `87` tests, `0` failures, `0` errors.
  - Full backend suite: `930` tests, `0` failures, `0` errors, `2` skipped.
  - `git diff --check`: no whitespace errors; only CRLF conversion warnings.

### Self-Review

- Covers real model batch evaluation: Tasks 3-5.
- Covers threshold calibration: existing batch report calibration is required by release gate and checked in Tasks 3-5.
- Covers prompt version management: existing prompt registry/hash release gate is exercised by Tasks 3-5.
- Covers failure-rate monitoring: existing failure metrics report is required by release gate and checked in Tasks 3-5.
- Covers advisor vs rule A/B: existing A/B report is required by release gate and checked in Tasks 3-5.
- Covers pre-release quality gate: release gate must pass or block with explicit reasons.
