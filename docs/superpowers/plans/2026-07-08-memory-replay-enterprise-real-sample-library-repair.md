# Memory Replay Enterprise Real Sample Library Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair the external real-human replay library so it is auditable, anonymized, label-consistent, and suitable as an enterprise-grade memory evaluation source.

**Architecture:** Keep the existing replay pipeline: seed cases, simulated cases, and public external real-human cases are loaded into one active dataset. Fix the problem at the source by tightening the memory-signal policy, the external harvester labels, and the committed external dataset artifact; do not weaken production quality gates to hide failures.

**Tech Stack:** Java 17, JUnit 5, AssertJ, Spring service classes, Jackson JSON resources, PowerShell harvester scripts.

---

### Task 1: Root Cause Guard Tests

**Files:**
- Modify: `backend/src/test/java/com/ainote/app/service/MemorySignalClassifierTest.java`
- Modify: `backend/src/test/java/com/ainote/app/service/LlmMemorySignalAdvisorTest.java`
- Modify: `backend/src/test/java/com/ainote/app/service/ExternalRealHumanReplayDatasetLoaderTest.java`

- [ ] **Step 1: Add classifier tests for incidental remember text**

Add cases proving that `remember` inside writing, grammar, recall, or quoted text is not an explicit memory request:

```java
for (String message : List.of(
        "write a rap about the 90s era and have every sentence start with the words, i remember.",
        "How can I recall or remember a music that I used to listen to as a child?",
        "he can't remember who he made an appointment with. check grammar",
        "give me different ways of saying \"remember that i will be waiting\"")) {
    MemorySignalClassifier.SignalClassification signals = classifier.classify(
            new MemoryCapturePolicy.CaptureRequest("user-1", message, "ok"));
    assertThat(signals.explicitRemember()).as(message).isFalse();
}
```

- [ ] **Step 2: Add advisor contract tests for fact and strict prompt instructions**

Assert that the advisor accepts `fact` as a valid memory type and that the prompt tells the model not to capture incidental `remember` usage.

- [ ] **Step 3: Add external dataset enterprise label tests**

Assert that known noisy external examples are denied, that no approved external sample uses unsupported memory types, and that every external manifest entry has approved provenance and redaction metadata.

- [ ] **Step 4: Run tests and verify they fail before implementation**

Run:

```powershell
mvn "-Dtest=MemorySignalClassifierTest,LlmMemorySignalAdvisorTest,ExternalRealHumanReplayDatasetLoaderTest" test
```

Expected: failures from the new tests before production/data fixes.

### Task 2: Production Policy And Advisor Contract

**Files:**
- Modify: `backend/src/main/java/com/ainote/app/service/MemorySignalClassifier.java`
- Modify: `backend/src/main/java/com/ainote/app/service/LlmMemorySignalAdvisor.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryAdvisorReplayEvaluationServiceTest.java`

- [ ] **Step 1: Replace broad explicit remember detection**

Change explicit memory detection from any `remember` token to directive-shaped requests such as `remember:`, `remember that my`, `please remember`, `keep in mind that`, `请记住`, and `记住：`. Exclude task-shaped text such as `how to remember`, `can't remember`, `ways of saying`, `write ... remember`, and quoted/story content.

- [ ] **Step 2: Add fact to the advisor schema**

Update `LlmMemorySignalAdvisor` to allow `fact`, `advisor_fact_signal`, and prompt version `memory-advisor-v2`.

- [ ] **Step 3: Merge high-confidence fact advice into capture signals**

Allow `MemorySignalClassifier` to use high-confidence advisor `fact` advice as a stable memory signal while preserving hard-deny precedence.

- [ ] **Step 4: Run focused tests**

Run:

```powershell
mvn "-Dtest=MemorySignalClassifierTest,LlmMemorySignalAdvisorTest,MemoryAdvisorReplayEvaluationServiceTest" test
```

Expected: all focused tests pass.

### Task 3: External Real-Human Artifact Repair

**Files:**
- Modify: `scripts/harvest-external-memory-replay.ps1`
- Modify: `backend/src/test/resources/memory/external-real-human-replay-dataset.json`

- [ ] **Step 1: Update harvester policy expectations**

Mirror the production explicit-memory rules in `Get-PolicyExpectation`, add `fact` only for stable first-person/project facts, and keep incidental `remember` text in `DENY_TRANSIENT`.

- [ ] **Step 2: Rewrite committed external artifact mechanically**

Reclassify the existing 2000 public WildChat-derived cases with the tightened rules. Preserve source references, conversation IDs, language tags, and redaction metadata. Regenerate IDs and scenario tags to match the corrected category.

- [ ] **Step 3: Run external dataset tests**

Run:

```powershell
mvn "-Dtest=ExternalRealHumanReplayDatasetLoaderTest" test
```

Expected: all external dataset tests pass.

### Task 4: Active Dataset And Offline Quality Gates

**Files:**
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryReplayDatasetLoaderTest.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java`

- [ ] **Step 1: Ensure the active dataset still has at least 4000 cases**

Keep the active dataset at 4000+ cases, with at least 2000 external real-human cases and 1800 simulated cases.

- [ ] **Step 2: Run replay policy quality gates**

Run:

```powershell
mvn "-Dtest=MemoryReplayDatasetLoaderTest,MemoryReplayEvaluationServiceTest,ExternalRealHumanReplayDatasetLoaderTest,MemoryReplaySampleReviewServiceTest" test
```

Expected: replay dataset shape, provenance, redaction, and rule-baseline quality gates pass.

- [ ] **Step 3: Recompute a no-cost oracle advisor readiness gate**

Run:

```powershell
mvn "-Dtest=MemoryAdvisorProductionQualityServiceTest,MemoryAdvisorFormalEvaluationServiceTest,MemoryAdvisorFormalBatchEvaluationServiceTest" test
```

Expected: oracle and batch infrastructure tests pass without paid model calls.

### Task 5: Final Verification

**Files:**
- All files changed in Tasks 1-4.

- [ ] **Step 1: Run backend unit test suite**

Run:

```powershell
mvn test
```

Expected: all backend tests pass.

- [ ] **Step 2: Inspect git status**

Run:

```powershell
git status --short --branch
```

Expected: only this repair's tracked changes plus pre-existing unrelated untracked files.

- [ ] **Step 3: Commit the repair**

Run:

```powershell
git add docs/superpowers/plans/2026-07-08-memory-replay-enterprise-real-sample-library-repair.md scripts/harvest-external-memory-replay.ps1 backend/src/main/java/com/ainote/app/service/MemorySignalClassifier.java backend/src/main/java/com/ainote/app/service/LlmMemorySignalAdvisor.java backend/src/test/java/com/ainote/app/service/MemorySignalClassifierTest.java backend/src/test/java/com/ainote/app/service/LlmMemorySignalAdvisorTest.java backend/src/test/java/com/ainote/app/service/ExternalRealHumanReplayDatasetLoaderTest.java backend/src/test/java/com/ainote/app/service/MemoryAdvisorReplayEvaluationServiceTest.java backend/src/test/resources/memory/external-real-human-replay-dataset.json
git commit -m "test: repair enterprise memory replay sample library"
```

Expected: commit succeeds and unrelated untracked files remain uncommitted.
