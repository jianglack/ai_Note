# Memory Replay 2000 Realistic Simulation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Raise the active memory replay evaluation corpus to 2000 cases by combining the existing 200-case seed dataset with 1800 deterministic `simulated_realistic` cases, with provenance, scenario, persona, review, and hygiene gates.

**Architecture:** Keep production memory behavior unchanged. Add test-side dataset loader and generator classes that combine `replay-eval-cases.json` with deterministic simulated cases, then update replay/advisor tests to evaluate the active 2000-case corpus. Provenance remains sidecar test metadata so `MemoryReplayEvaluationService.MemoryReplayCase` stays compatible.

**Tech Stack:** Java 17, JUnit 5, AssertJ, Jackson, Spring Boot backend tests, JSON test resources, PowerShell on Windows with explicit UTF-8 checks.

---

## Source Spec

Read before execution:

- `docs/superpowers/specs/2026-07-08-memory-replay-realistic-simulation-and-real-sample-loop-design.md`

## File Responsibility Map

- `backend/src/test/java/com/ainote/app/service/MemoryReplayDataset.java`
  - New test-side dataset model containing active cases and manifest entries.
- `backend/src/test/java/com/ainote/app/service/MemoryReplayDatasetLoader.java`
  - New test-side loader that reads `replay-eval-cases.json`, labels those cases `synthetic_seed`, appends generated `simulated_realistic` cases, and exposes active cases plus manifest.
- `backend/src/test/java/com/ainote/app/service/SimulatedMemoryReplayCaseGenerator.java`
  - New deterministic generator for 1800 simulated realistic cases across the 15 replay categories, persona agents, and scenario tags.
- `backend/src/test/java/com/ainote/app/service/MemoryReplayDatasetLoaderTest.java`
  - New tests for 2000-case corpus size, source type governance, persona/scenario coverage, duplicate protection, and hygiene.
- `backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java`
  - Update active replay loading and shape gates from 200 to 2000.
- `backend/src/test/java/com/ainote/app/service/MemoryAdvisorReplayEvaluationServiceTest.java`
  - Update active replay loading and advisor oracle expectation to 2000 cases.
- `docs/superpowers/plans/2026-07-08-memory-replay-2000-realistic-simulation.md`
  - This implementation plan.

## Dataset Rules

The active corpus must satisfy:

- total active cases exactly 2000 or at least 2000 with no unreviewed overage;
- Chinese case IDs at least 1300;
- allow cases at least 700;
- deny cases at least 700;
- category minimums:
  - `operation`: 150
  - `explicit_preference`: 150
  - `implicit_preference`: 180
  - `style`: 180
  - `correction`: 140
  - `project_context`: 140
  - `reference_only`: 150
  - `rag_reference`: 150
  - `one_off`: 140
  - `assistant_feedback`: 100
  - `sensitive`: 100
  - `forget`: 100
  - `ambiguous`: 120
  - `multi_turn_correction`: 100
  - `complex_project_context`: 100
- every active case has a manifest entry;
- `synthetic_seed` cases are the existing JSON cases;
- generated cases are `simulated_realistic`, never `real_user_anonymized`;
- every generated case has nonblank `conversationId`, `sourceReference`, `personaAgent`, `scenarioTags`, `languageTags`, `redactionReportId`, `reviewStatus=approved`, `reviewer`, and `approvedAt`;
- no committed or generated active case contains `\uFFFD`, `sk-test123`, real-looking AWS keys, emails, phone numbers, or real domains;
- replay metrics remain strict at 1.0.

## Task 1: Add Dataset Loader Contract Tests

**Files:**
- Create: `backend/src/test/java/com/ainote/app/service/MemoryReplayDatasetLoaderTest.java`

- [ ] **Step 1: Create failing loader contract test**

Create `MemoryReplayDatasetLoaderTest` with tests that call a not-yet-implemented `MemoryReplayDatasetLoader.loadActiveDataset()` and assert:

```java
assertThat(dataset.cases()).hasSizeGreaterThanOrEqualTo(2000);
assertThat(dataset.manifest()).hasSize(dataset.cases().size());
assertThat(dataset.cases()).extracting(MemoryReplayEvaluationService.MemoryReplayCase::id).doesNotHaveDuplicates();
assertThat(dataset.cases()).extracting(MemoryReplayEvaluationService.MemoryReplayCase::userMessage).doesNotHaveDuplicates();
assertThat(dataset.manifest().stream().filter(entry -> "simulated_realistic".equals(entry.sourceType())).count())
        .isGreaterThanOrEqualTo(1800);
assertThat(dataset.manifest()).allSatisfy(entry -> assertThat(entry.reviewStatus()).isEqualTo("approved"));
assertThat(dataset.manifest()).noneSatisfy(entry -> assertThat(entry.sourceType()).isEqualTo("real_user_anonymized"));
```

Also assert persona count >= 10, scenario tag coverage includes all scenario families from the spec, no replacement characters, and no scanner-risk secrets.

- [ ] **Step 2: Run RED**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryReplayDatasetLoaderTest" test
```

Expected: compile failure because `MemoryReplayDatasetLoader` and `MemoryReplayDataset` do not exist.

- [ ] **Step 3: Commit RED test**

Run:

```powershell
cd D:\ainotetest
git add backend/src/test/java/com/ainote/app/service/MemoryReplayDatasetLoaderTest.java
git commit -m "test: require governed replay dataset loader"
```

## Task 2: Implement Active Dataset Loader And Manifest Model

**Files:**
- Create: `backend/src/test/java/com/ainote/app/service/MemoryReplayDataset.java`
- Create: `backend/src/test/java/com/ainote/app/service/MemoryReplayDatasetLoader.java`
- Create: `backend/src/test/java/com/ainote/app/service/SimulatedMemoryReplayCaseGenerator.java`

- [ ] **Step 1: Implement `MemoryReplayDataset`**

Create records:

```java
record MemoryReplayDataset(
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
        List<ManifestEntry> manifest) {
}

record ManifestEntry(
        String caseId,
        String sourceType,
        String sourceReference,
        String personaAgent,
        List<String> scenarioTags,
        List<String> languageTags,
        String conversationId,
        String redactionReportId,
        String reviewStatus,
        String reviewer,
        String approvedAt) {
}
```

Normalize null strings to `""` and null lists to `List.of()`.

- [ ] **Step 2: Implement loader**

`MemoryReplayDatasetLoader.loadActiveDataset()` should:

1. Read `/memory/replay-eval-cases.json` using Jackson.
2. Create `synthetic_seed` manifest entries for all existing cases.
3. Call `SimulatedMemoryReplayCaseGenerator.generate(1800)`.
4. Concatenate seed and generated cases.
5. Fail fast with `IllegalStateException` if there are duplicate IDs or duplicate user messages.

- [ ] **Step 3: Implement deterministic generator**

The generator should emit exactly 1800 `simulated_realistic` cases. Generate by category with counts equal to the 2000 target minus existing seed counts. Use supported policy patterns only:

- allow explicit: `remember:` / `记住：`
- allow implicit: `I prefer`, `I like`, `我希望`, `我喜欢`, `我偏好`
- allow style: `from now on`, `always`, `以后`, `今后`
- allow correction: `instead`, `no longer`, `以后不要`, `不再`, `改为`
- allow project: `project context:` / `项目上下文：`
- deny reference/RAG: selected note/current note or `<rag_context role="reference_only">`
- deny operation: delete/confirm/cancel/ok/yes/no and Chinese equivalents
- deny one-off: `this reply`, `this time`, `这次`, `本轮`
- deny assistant feedback: current answer/reply praise or complaint
- deny sensitive: only fake placeholders such as `fake-api-key-test-123`
- deny forget: forget/不要记住/忘记
- deny ambiguous: neutral low-signal utterances with empty expected signals

Each generated case ID should use:

```text
replay_<cn|en>_<category>_sim_<zero-padded-index>
```

Each generated manifest entry should include:

- `sourceType=simulated_realistic`
- `sourceReference=generator:SimulatedMemoryReplayCaseGenerator:v1`
- one of the 12 user persona agents
- scenario tags matching the category and interference context
- `reviewStatus=approved`
- `reviewer=consistency_reviewer_v1`
- `redactionReportId=redaction-simulated-v1`
- `approvedAt=2026-07-08T00:00:00Z`

- [ ] **Step 4: Run loader tests GREEN**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryReplayDatasetLoaderTest" test
```

Expected: build success.

- [ ] **Step 5: Commit loader and generator**

Run:

```powershell
cd D:\ainotetest
git add backend/src/test/java/com/ainote/app/service/MemoryReplayDataset.java backend/src/test/java/com/ainote/app/service/MemoryReplayDatasetLoader.java backend/src/test/java/com/ainote/app/service/SimulatedMemoryReplayCaseGenerator.java backend/src/test/java/com/ainote/app/service/MemoryReplayDatasetLoaderTest.java
git commit -m "test: add governed replay dataset generator"
```

## Task 3: Raise Active Replay Gates To 2000

**Files:**
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryAdvisorReplayEvaluationServiceTest.java`

- [ ] **Step 1: Use `MemoryReplayDatasetLoader` in replay tests**

Replace local `loadCases()` JSON-only loading with:

```java
return MemoryReplayDatasetLoader.loadActiveDataset().cases();
```

- [ ] **Step 2: Raise constants and category minimums**

Use:

```java
private static final int MIN_TOTAL_CASES = 2000;
private static final int MIN_CHINESE_CASES = 1300;
private static final int MIN_ALLOW_CASES = 700;
private static final int MIN_DENY_CASES = 700;
```

Scale category minimums to the 2000 target from the spec.

- [ ] **Step 3: Update advisor replay test**

Load the active dataset through `MemoryReplayDatasetLoader` and assert total cases >= 2000. Keep duplicate `userMessage` oracle guard.

- [ ] **Step 4: Run replay/advisor tests**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryReplayEvaluationServiceTest,MemoryAdvisorReplayEvaluationServiceTest,MemoryReplayDatasetLoaderTest" test
```

Expected: build success, all replay quality metrics remain 1.0.

- [ ] **Step 5: Commit active gate change**

Run:

```powershell
cd D:\ainotetest
git add backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java backend/src/test/java/com/ainote/app/service/MemoryAdvisorReplayEvaluationServiceTest.java
git commit -m "test: raise memory replay corpus to two thousand cases"
```

## Task 4: Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused replay tests**

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryReplayEvaluationServiceTest,MemoryAdvisorReplayEvaluationServiceTest,MemoryReplayDatasetLoaderTest" test
```

- [ ] **Step 2: Run focused memory suite**

```powershell
mvn "-Dtest=MemoryReplayEvaluationServiceTest,MemoryAdvisorReplayEvaluationServiceTest,MemoryReplayDatasetLoaderTest,MemorySystemEvaluationTest,MemoryCapturePolicyTest,MemoryCandidateExtractorTest,MemorySignalClassifierTest" test
```

- [ ] **Step 3: Run broader memory regression suite**

```powershell
mvn "-Dtest=MemoryReplayEvaluationServiceTest,MemoryAdvisorReplayEvaluationServiceTest,MemoryReplayDatasetLoaderTest,MemorySystemEvaluationTest,MemoryCapturePolicyTest,MemoryCandidateExtractorTest,MemorySignalClassifierTest,MemoryControlServiceTest,MemoryControllerTest,MemoryRetrievalServiceTest,MemoryWriteServiceTest,MemoryOrchestratorTest,ContextAssemblerTest,ReliableChatMemoryStoreFlushTest" test
```

- [ ] **Step 4: Run full backend suite**

```powershell
mvn test
```

## Task 5: Final Review

**Files:**
- Read all changed files.

- [ ] **Step 1: Run final stats**

Use a UTF-8-safe local script or test output to report:

- total cases;
- source type counts;
- Chinese case count;
- allow/deny counts;
- category counts;
- persona count;
- scenario tag coverage;
- duplicate IDs/messages;
- PII/secret hygiene result.

- [ ] **Step 2: Run git checks**

```powershell
cd D:\ainotetest
git diff --check main..HEAD
git status --short --branch
git log --oneline --decorate main..HEAD
git diff --stat main..HEAD
```

- [ ] **Step 3: Report scope honestly**

Final report must say:

- 2000 active replay cases are achieved through `synthetic_seed` plus `simulated_realistic`.
- The new cases are not real user samples.
- The real-user loop is guarded by provenance rules but still needs an approved real data source before `real_user_anonymized` samples can be added.

## Self-Review

- Spec coverage: covers 2000 active cases, provenance, simulated-vs-real boundary, persona/scenario coverage, and deterministic replay gates.
- Placeholder scan: no task asks the implementer to invent unspecified behavior.
- Type consistency: all helper classes live in the same test package as existing replay tests.
- Scope check: no production runtime behavior, frontend UI, database migration, or live LLM call is included.
