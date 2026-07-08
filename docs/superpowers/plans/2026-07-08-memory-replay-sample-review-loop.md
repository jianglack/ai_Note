# Memory Replay Sample Review Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Use the current 2000-case active replay dataset to establish the next governance loop: sample staging, provenance review, redaction checks, and a real-user pilot gate.

**Architecture:** Keep the active replay evaluator unchanged. Add a test-side review service that consumes `MemoryReplayDataset`, classifies approved `synthetic_seed` and `simulated_realistic` cases as a simulation baseline, quarantines unsafe or misrepresented samples, and reports that the real-user pilot is not ready until approved `real_user_anonymized` samples exist. This turns the current data into an auditable intake loop without claiming simulated samples are real user data.

**Tech Stack:** Java 17, JUnit 5, AssertJ, Maven Surefire, deterministic test-side dataset classes.

---

## Source Spec

Read before execution:

- `docs/superpowers/specs/2026-07-08-memory-replay-realistic-simulation-and-real-sample-loop-design.md`

## File Responsibility Map

- `backend/src/test/java/com/ainote/app/service/MemoryReplaySampleReviewService.java`
  - New test-side service that audits a `MemoryReplayDataset` and returns per-case review records plus aggregate gates.
- `backend/src/test/java/com/ainote/app/service/MemoryReplaySampleReviewServiceTest.java`
  - New tests proving the existing active dataset is usable as a simulation baseline and proving unsafe or fake real-user samples are quarantined.
- `docs/superpowers/plans/2026-07-08-memory-replay-sample-review-loop.md`
  - This plan.

## Review Rules

- `synthetic_seed` and `simulated_realistic` can be approved for active evaluation only when their manifest metadata is complete, review status is `approved`, and content hygiene checks pass.
- `synthetic_seed` and `simulated_realistic` are never eligible as real-user samples.
- `real_user_anonymized` can be eligible for the real-user pilot only when source reference, redaction report, reviewer, approval timestamp, scenario tags, language tags, and `reviewStatus=approved` are present.
- Any sample with email, phone number, non-example domain, AWS-looking key, known test secret, or replacement character is quarantined.
- The real-user pilot gate requires at least 50 approved `real_user_anonymized` samples.
- The current 2000-case dataset should pass as a simulation baseline and fail only the real-user pilot readiness gate.

## Task 1: Add Review Loop Contract Tests

**Files:**
- Create: `backend/src/test/java/com/ainote/app/service/MemoryReplaySampleReviewServiceTest.java`

- [ ] **Step 1: Write failing test for current active dataset**

Create a test that calls:

```java
MemoryReplayDataset dataset = MemoryReplayDatasetLoader.loadActiveDataset();
MemoryReplaySampleReviewService.SampleReviewReport report =
        MemoryReplaySampleReviewService.review(dataset);
```

Assert:

```java
assertThat(report.totalCases()).isEqualTo(dataset.cases().size());
assertThat(report.approvedActiveEvaluationCases()).isEqualTo(dataset.cases().size());
assertThat(report.quarantinedCases()).isZero();
assertThat(report.realUserApprovedCases()).isZero();
assertThat(report.realUserPilotReady()).isFalse();
assertThat(report.gateFailures()).contains("real_user_pilot_requires_50_approved_samples");
assertThat(report.sourceCounts()).containsEntry("synthetic_seed", 200L);
assertThat(report.sourceCounts()).containsEntry("simulated_realistic", 1800L);
assertThat(report.records()).allSatisfy(record -> {
    assertThat(record.approvedForActiveEvaluation()).isTrue();
    assertThat(record.eligibleAsRealUserSample()).isFalse();
    assertThat(record.intakeBucket()).isEqualTo("simulation_baseline");
});
```

- [ ] **Step 2: Write failing test for unsafe or misrepresented samples**

Build a two-case `MemoryReplayDataset` inline:

1. A `real_user_anonymized` case containing `alice@example.org` with blank redaction metadata.
2. A `simulated_realistic` case with `reviewStatus=pending_review`.

Assert both are quarantined, active approvals are zero, and per-case flags include:

```java
"contains_email"
"missing_redaction_report_id"
"review_not_approved"
```

- [ ] **Step 3: Run RED**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryReplaySampleReviewServiceTest" test
```

Expected: compile failure because `MemoryReplaySampleReviewService` does not exist.

- [ ] **Step 4: Commit RED test**

Run:

```powershell
cd D:\ainotetest
git add backend/src/test/java/com/ainote/app/service/MemoryReplaySampleReviewServiceTest.java docs/superpowers/plans/2026-07-08-memory-replay-sample-review-loop.md
git commit -m "test: require memory replay sample review loop"
```

## Task 2: Implement Review Service

**Files:**
- Create: `backend/src/test/java/com/ainote/app/service/MemoryReplaySampleReviewService.java`

- [ ] **Step 1: Implement report records**

Create package-private records:

```java
record SampleReviewReport(
        int totalCases,
        long approvedActiveEvaluationCases,
        long quarantinedCases,
        long realUserApprovedCases,
        boolean realUserPilotReady,
        Map<String, Long> sourceCounts,
        List<SampleReviewRecord> records,
        List<String> gateFailures) {
}

record SampleReviewRecord(
        String caseId,
        String sourceType,
        String intakeBucket,
        boolean approvedForActiveEvaluation,
        boolean eligibleAsRealUserSample,
        List<String> flags) {
}
```

- [ ] **Step 2: Implement metadata and hygiene checks**

For each case, join `userMessage` and `assistantOutput`, then add flags for:

- `unsupported_source_type`
- `missing_source_reference`
- `missing_redaction_report_id`
- `missing_reviewer`
- `missing_approved_at`
- `missing_scenario_tags`
- `missing_language_tags`
- `review_not_approved`
- `contains_replacement_character`
- `contains_known_test_secret`
- `contains_email`
- `contains_phone`
- `contains_real_domain`
- `contains_aws_key`

Use the same conservative regex patterns as `MemoryReplayDatasetLoaderTest`.

- [ ] **Step 3: Implement source decisions**

Rules:

```java
boolean approved = flags.isEmpty();
boolean realUserSource = "real_user_anonymized".equals(sourceType);
boolean simulationSource = "synthetic_seed".equals(sourceType) || "simulated_realistic".equals(sourceType);
String bucket = approved && simulationSource ? "simulation_baseline"
        : approved && realUserSource ? "real_user_candidate"
        : "quarantine";
boolean eligibleAsRealUserSample = approved && realUserSource;
boolean approvedForActiveEvaluation = approved && (simulationSource || realUserSource);
```

After all records, set:

```java
boolean realUserPilotReady = realUserApprovedCases >= 50;
```

If not ready, add:

```java
"real_user_pilot_requires_50_approved_samples"
```

- [ ] **Step 4: Run GREEN**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryReplaySampleReviewServiceTest" test
```

Expected: all tests pass.

- [ ] **Step 5: Commit implementation**

Run:

```powershell
cd D:\ainotetest
git add backend/src/test/java/com/ainote/app/service/MemoryReplaySampleReviewService.java backend/src/test/java/com/ainote/app/service/MemoryReplaySampleReviewServiceTest.java
git commit -m "test: add memory replay sample review loop"
```

## Task 3: Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused review and replay tests**

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryReplaySampleReviewServiceTest,MemoryReplayDatasetLoaderTest,MemoryReplayEvaluationServiceTest,MemoryAdvisorReplayEvaluationServiceTest" test
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
```

## Final Report Requirements

Report clearly:

- The 2000 current samples are now used as a reviewed `simulation_baseline`.
- The real-user pilot gate remains blocked because there are still 0 approved `real_user_anonymized` samples.
- Unsafe or under-reviewed samples are quarantined by automated tests.
- This is the next governance loop, not a claim that simulated data is real production data.

## Self-Review

- Spec coverage: implements intake/review gates for simulated and future real-user sources.
- Placeholder scan: no open-ended TODOs or invented future steps remain.
- Type consistency: all records are package-private and live next to existing replay test helpers.
- Scope check: no production import endpoint, database migration, frontend UI, or raw real user data is included.
