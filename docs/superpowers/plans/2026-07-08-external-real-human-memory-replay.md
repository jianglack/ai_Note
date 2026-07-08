# External Real Human Memory Replay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add at least 2000 filtered, sanitized, public real-human conversation replay cases to the active memory replay dataset without mislabeling them as product-local user data.

**Architecture:** Add an external replay resource and loader, then append it to `MemoryReplayDatasetLoader.loadActiveDataset()`. External cases are fixed replay cases with fixed expectations and sidecar manifest metadata. A separate harvester script documents how the artifact was produced from Hugging Face Dataset Viewer, but unit tests remain deterministic and offline.

**Tech Stack:** Java 17, JUnit 5, AssertJ, Jackson, Maven Surefire, PowerShell, Hugging Face Dataset Viewer API.

---

## Source Spec

Read before execution:

- `docs/superpowers/specs/2026-07-08-external-real-human-memory-replay-design.md`

## File Responsibility Map

- `backend/src/test/resources/memory/external-real-human-replay-dataset.json`
  - New committed, sanitized external replay artifact containing `cases` and `manifest`.
- `backend/src/test/java/com/ainote/app/service/ExternalRealHumanReplayDatasetLoader.java`
  - New loader for the external replay artifact.
- `backend/src/test/java/com/ainote/app/service/ExternalRealHumanReplayDatasetLoaderTest.java`
  - New tests for external source count, source type, project-relevance tags, provenance, and hygiene.
- `backend/src/test/java/com/ainote/app/service/MemoryReplayDatasetLoader.java`
  - Modify to append external replay dataset after seed and simulated datasets.
- `backend/src/test/java/com/ainote/app/service/MemoryReplayDatasetLoaderTest.java`
  - Modify source-type and count gates to include `external_real_human_conversation`.
- `backend/src/test/java/com/ainote/app/service/MemoryReplaySampleReviewService.java`
  - Modify supported source types and report counters for external real-human data.
- `backend/src/test/java/com/ainote/app/service/MemoryReplaySampleReviewServiceTest.java`
  - Modify tests to assert external data is active-eval approved but not product-user pilot data.
- `backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java`
  - Raise active corpus minimum from 2000 to 4000.
- `backend/src/test/java/com/ainote/app/service/MemoryAdvisorReplayEvaluationServiceTest.java`
  - Raise active corpus minimum from 2000 to 4000.
- `scripts/harvest-external-memory-replay.ps1`
  - New documented one-shot harvester for reproducing the external artifact from WildChat.

## Dataset Rules

- Add exactly or at least 2000 `external_real_human_conversation` cases.
- Every external case must have:
  - ID matching `replay_<lang>_<category>_external_<index>`;
  - source reference containing `hf://datasets/allenai/WildChat/default/train`;
  - `reviewStatus=approved`;
  - `reviewer=external_replay_curator_v1`;
  - `redactionReportId=redaction-external-wildchat-v1`;
  - scenario tag `external_real_human_conversation`;
  - at least one project-relevance tag such as `external_memory_preference`, `external_style_request`, `external_correction`, `external_work_context`, `external_reference_task`, `external_one_off`, `external_assistant_feedback`, `external_memory_control`, `external_sensitive_boundary`, or `external_ambiguous`.
- External cases may be English-heavy if they pass the project-relevance filter; Chinese coverage remains protected by existing 1300+ Chinese active cases.
- No live network calls in JUnit tests.

## Task 1: Add External Source Contract Tests

**Files:**
- Create: `backend/src/test/java/com/ainote/app/service/ExternalRealHumanReplayDatasetLoaderTest.java`

- [ ] **Step 1: Write failing loader test**

Add tests that call `ExternalRealHumanReplayDatasetLoader.load()` and assert:

```java
MemoryReplayDataset dataset = ExternalRealHumanReplayDatasetLoader.load();
assertThat(dataset.cases()).hasSizeGreaterThanOrEqualTo(2000);
assertThat(dataset.manifest()).hasSameSizeAs(dataset.cases());
assertThat(dataset.manifest()).allSatisfy(entry -> {
    assertThat(entry.sourceType()).isEqualTo("external_real_human_conversation");
    assertThat(entry.sourceReference()).contains("hf://datasets/allenai/WildChat/default/train");
    assertThat(entry.reviewStatus()).isEqualTo("approved");
    assertThat(entry.reviewer()).isEqualTo("external_replay_curator_v1");
    assertThat(entry.redactionReportId()).isEqualTo("redaction-external-wildchat-v1");
    assertThat(entry.scenarioTags()).contains("external_real_human_conversation");
});
```

Also assert no duplicate IDs, no duplicate user messages, no PII/secret patterns, and that project-relevance tags contain at least:

```java
external_memory_preference
external_style_request
external_correction
external_work_context
external_reference_task
external_one_off
external_assistant_feedback
external_memory_control
external_ambiguous
```

- [ ] **Step 2: Run RED**

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=ExternalRealHumanReplayDatasetLoaderTest" test
```

Expected: compile failure because `ExternalRealHumanReplayDatasetLoader` does not exist.

- [ ] **Step 3: Commit RED test**

```powershell
cd D:\ainotetest
git add backend/src/test/java/com/ainote/app/service/ExternalRealHumanReplayDatasetLoaderTest.java docs/superpowers/plans/2026-07-08-external-real-human-memory-replay.md
git commit -m "test: require external real human replay dataset"
```

## Task 2: Harvest External Replay Artifact

**Files:**
- Create: `scripts/harvest-external-memory-replay.ps1`
- Create: `backend/src/test/resources/memory/external-real-human-replay-dataset.json`

- [ ] **Step 1: Add harvester script**

The script should:

1. Query `https://datasets-server.huggingface.co/search` for WildChat using project-relevance query terms.
2. Read each matching row's first user message and first assistant response.
3. Reject toxic rows, flagged rows, obvious PII/secrets/domains/phone-like values, replacement characters, and very long messages.
4. Assign category and scenario tags using keyword filters.
5. Store sanitized replay cases and manifest in `external-real-human-replay-dataset.json`.

- [ ] **Step 2: Generate artifact**

Run:

```powershell
cd D:\ainotetest
powershell -ExecutionPolicy Bypass -File scripts/harvest-external-memory-replay.ps1
```

Expected: writes at least 2000 external cases.

- [ ] **Step 3: Commit harvester and artifact**

```powershell
git add scripts/harvest-external-memory-replay.ps1 backend/src/test/resources/memory/external-real-human-replay-dataset.json
git commit -m "test: add external real human replay artifact"
```

## Task 3: Implement External Loader

**Files:**
- Create: `backend/src/test/java/com/ainote/app/service/ExternalRealHumanReplayDatasetLoader.java`

- [ ] **Step 1: Implement loader**

Read `/memory/external-real-human-replay-dataset.json` into a resource record:

```java
record ExternalReplayResource(
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases,
        List<MemoryReplayDataset.ManifestEntry> manifest) {
}
```

Return `new MemoryReplayDataset(resource.cases(), resource.manifest())`.

Validate:

- nonempty cases;
- same manifest size;
- manifest case IDs match cases;
- duplicate IDs and duplicate user messages fail fast.

- [ ] **Step 2: Run GREEN**

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=ExternalRealHumanReplayDatasetLoaderTest" test
```

- [ ] **Step 3: Commit loader**

```powershell
cd D:\ainotetest
git add backend/src/test/java/com/ainote/app/service/ExternalRealHumanReplayDatasetLoader.java backend/src/test/java/com/ainote/app/service/ExternalRealHumanReplayDatasetLoaderTest.java
git commit -m "test: load external real human replay dataset"
```

## Task 4: Integrate External Dataset Into Active Replay

**Files:**
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryReplayDatasetLoader.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryReplayDatasetLoaderTest.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryReplaySampleReviewService.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryReplaySampleReviewServiceTest.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryAdvisorReplayEvaluationServiceTest.java`

- [ ] **Step 1: Append external dataset in active loader**

In `MemoryReplayDatasetLoader.loadActiveDataset()`, call:

```java
MemoryReplayDataset externalDataset = ExternalRealHumanReplayDatasetLoader.load();
activeCases.addAll(externalDataset.cases());
activeManifest.addAll(externalDataset.manifest());
```

- [ ] **Step 2: Update dataset loader gates**

Use:

```java
private static final int MIN_TOTAL_CASES = 4000;
private static final int MIN_EXTERNAL_REAL_HUMAN_CASES = 2000;
```

Allow source type `external_real_human_conversation` and assert its source count.

- [ ] **Step 3: Update sample review service**

Add supported source type `external_real_human_conversation`.

Add report count:

```java
long externalRealHumanApprovedCases
```

External cases are approved for active evaluation when metadata and hygiene checks pass, but `eligibleAsRealUserSample` remains false because that field is reserved for product-local real-user data.

- [ ] **Step 4: Update replay/advisor minimums**

Set active dataset minimums to 4000 in replay and advisor tests.

- [ ] **Step 5: Run integrated tests**

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=ExternalRealHumanReplayDatasetLoaderTest,MemoryReplayDatasetLoaderTest,MemoryReplaySampleReviewServiceTest,MemoryReplayEvaluationServiceTest,MemoryAdvisorReplayEvaluationServiceTest" test
```

- [ ] **Step 6: Commit integration**

```powershell
cd D:\ainotetest
git add backend/src/test/java/com/ainote/app/service/MemoryReplayDatasetLoader.java backend/src/test/java/com/ainote/app/service/MemoryReplayDatasetLoaderTest.java backend/src/test/java/com/ainote/app/service/MemoryReplaySampleReviewService.java backend/src/test/java/com/ainote/app/service/MemoryReplaySampleReviewServiceTest.java backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java backend/src/test/java/com/ainote/app/service/MemoryAdvisorReplayEvaluationServiceTest.java
git commit -m "test: include external real human replay in active dataset"
```

## Task 5: Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused memory replay suite**

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=ExternalRealHumanReplayDatasetLoaderTest,MemoryReplayDatasetLoaderTest,MemoryReplaySampleReviewServiceTest,MemoryReplayEvaluationServiceTest,MemoryAdvisorReplayEvaluationServiceTest" test
```

- [ ] **Step 2: Run full backend suite**

```powershell
mvn test
```

- [ ] **Step 3: Run git checks**

```powershell
cd D:\ainotetest
git diff --check main..HEAD
git status --short --branch
git log --oneline --decorate main..HEAD
git diff --stat main..HEAD
```

## Final Report Requirements

Report:

- active replay now includes 2000+ external real-human conversation cases;
- external cases are filtered for project-relevant memory scenarios;
- external cases are not product-local user data;
- existing product-local pilot still depends on approved `product_real_user_anonymized` data;
- focused and full test results.

## Self-Review

- Spec coverage: source type, filter, redaction, loader, active integration, and sample review gates are covered.
- Placeholder scan: no unresolved placeholders remain.
- Type consistency: all new classes live in the existing test package and use existing `MemoryReplayDataset`.
- Scope check: no production endpoint and no live network call in tests.
