# Memory Advisor Production Closed Loop V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the task 2 production-quality closed loop for the LLM memory advisor: real-model batch report enrichment, raw-result calibration, prompt governance, A/B comparison, failure metrics, and release gate packaging.

**Architecture:** Keep the existing formal evaluation and production quality services as the evaluation core. Add small services around them for prompt metadata, raw advisor output, calibration, A/B comparison, failure metrics, and release readiness gating, then integrate those reports into `MemoryAdvisorFormalBatchEvaluationService.BatchEvaluationReport`.

**Tech Stack:** Java 17, Spring service classes, Jackson records, JUnit 5, AssertJ, Maven Surefire, PowerShell commands.

---

## Source Spec

Read before execution:

- `docs/superpowers/specs/2026-07-08-memory-advisor-production-closed-loop-v2-design.md`

## File Responsibility Map

- Create `backend/src/main/java/com/ainote/app/service/MemoryAdvisorPromptRegistry.java`
  - Stores prompt metadata for `memory-advisor-v2`, canonical prompt template, SHA-256 hash, status, allowed memory types, allowed signals, and compatible model families.
- Create `backend/src/main/java/com/ainote/app/service/MemoryAdvisorRawSignalAdvisor.java`
  - Optional interface for advisors that can expose raw model decisions before runtime confidence gating.
- Create `backend/src/main/java/com/ainote/app/service/MemoryAdvisorRawResult.java`
  - Serializable record containing raw decision fields, parse status, unavailable reason, and final gated `MemorySignalAdvisor.AdvisorResult`.
- Modify `backend/src/main/java/com/ainote/app/service/LlmMemorySignalAdvisor.java`
  - Implement `MemoryAdvisorRawSignalAdvisor`.
  - Expose canonical prompt template through reusable methods used by both runtime prompt generation and the prompt registry.
  - Preserve existing `advise` behavior by returning the final gated result from `adviseRaw`.
- Modify `backend/src/main/java/com/ainote/app/service/MemoryAdvisorFormalBatchEvaluationService.java`
  - Capture raw result in `ProgressEntry`.
  - Build calibration, A/B comparison, failure metrics, prompt metadata, and release gate decision into the batch report.
- Create `backend/src/main/java/com/ainote/app/service/MemoryAdvisorCalibrationService.java`
  - Computes threshold candidate metrics from raw parsed results only.
- Create `backend/src/main/java/com/ainote/app/service/MemoryAdvisorAbComparisonService.java`
  - Compares final advisor decisions against rule-policy baseline on the same replay cases.
- Create `backend/src/main/java/com/ainote/app/service/MemoryAdvisorFailureMetricsService.java`
  - Summarizes availability, timeout, error, retry, latency, and top unavailable reasons from batch progress entries.
- Create `backend/src/main/java/com/ainote/app/service/MemoryAdvisorReleaseGateService.java`
  - Validates complete release-readiness packages, prompt status, prompt hash, production gates, calibration, A/B comparison, failure metrics, and report freshness.
- Modify `backend/src/test/java/com/ainote/app/service/LlmMemorySignalAdvisorTest.java`
  - Add raw-result and canonical prompt/hash regression coverage.
- Create `backend/src/test/java/com/ainote/app/service/MemoryAdvisorPromptRegistryTest.java`
  - Tests prompt metadata, hash stability, and hash change sensitivity.
- Create `backend/src/test/java/com/ainote/app/service/MemoryAdvisorCalibrationServiceTest.java`
  - Tests threshold math and excluded unavailable/parse-error rows.
- Create `backend/src/test/java/com/ainote/app/service/MemoryAdvisorAbComparisonServiceTest.java`
  - Tests baseline/advisor deltas and sensitive advisor-only allow detection.
- Create `backend/src/test/java/com/ainote/app/service/MemoryAdvisorFailureMetricsServiceTest.java`
  - Tests unavailable, timeout, error, retry, latency, and reason aggregation.
- Create `backend/src/test/java/com/ainote/app/service/MemoryAdvisorReleaseGateServiceTest.java`
  - Tests stale v1 prompt, bare readiness report, missing package components, stale report, candidate approval action, and full pass.
- Modify `backend/src/test/java/com/ainote/app/service/MemoryAdvisorFormalBatchEvaluationServiceTest.java`
  - Tests persisted batch report includes the complete release-readiness package.
- Modify `backend/src/main/resources/application.yml.example`
  - Document `MEMORY_ADVISOR_RELEASE_GATE_MAX_REPORT_AGE_HOURS`.

## Task 1: Prompt Registry And Canonical Prompt

**Files:**
- Create: `backend/src/main/java/com/ainote/app/service/MemoryAdvisorPromptRegistry.java`
- Modify: `backend/src/main/java/com/ainote/app/service/LlmMemorySignalAdvisor.java`
- Test: `backend/src/test/java/com/ainote/app/service/MemoryAdvisorPromptRegistryTest.java`
- Test: `backend/src/test/java/com/ainote/app/service/LlmMemorySignalAdvisorTest.java`

- [ ] **Step 1: Write failing prompt registry tests**

Create `MemoryAdvisorPromptRegistryTest`:

```java
package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAdvisorPromptRegistryTest {

    private final MemoryAdvisorPromptRegistry registry = new MemoryAdvisorPromptRegistry();

    @Test
    void currentPromptMetadataMatchesV2AndUsesSha256Hash() {
        MemoryAdvisorPromptRegistry.PromptMetadata metadata = registry.current();

        assertThat(metadata.promptVersion()).isEqualTo("memory-advisor-v2");
        assertThat(metadata.status()).isEqualTo(MemoryAdvisorPromptRegistry.PromptStatus.CANDIDATE);
        assertThat(metadata.schemaVersion()).isEqualTo("memory-advisor-json-v1");
        assertThat(metadata.promptHash()).matches("[a-f0-9]{64}");
        assertThat(metadata.allowedMemoryTypes()).containsExactlyInAnyOrder(
                "fact", "preference", "style", "project_context", "none");
        assertThat(metadata.allowedSignals()).containsExactlyInAnyOrder(
                "advisor_fact_signal",
                "advisor_preference_signal",
                "advisor_interaction_style_signal",
                "advisor_project_context_signal");
        assertThat(metadata.compatibleModelFamilies()).contains("deepseek");
    }

    @Test
    void unknownPromptVersionIsNotRegistered() {
        assertThat(registry.find("memory-advisor-v1")).isEmpty();
    }

    @Test
    void canonicalHashChangesWhenTemplateChanges() {
        String originalHash = registry.hashCanonicalTemplate("system\nuser\nschema");
        String changedHash = registry.hashCanonicalTemplate("system\nuser\nschema changed");

        assertThat(originalHash).matches("[a-f0-9]{64}");
        assertThat(changedHash).matches("[a-f0-9]{64}");
        assertThat(changedHash).isNotEqualTo(originalHash);
    }
}
```

- [ ] **Step 2: Run RED**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorPromptRegistryTest" test
```

Expected: compilation fails because `MemoryAdvisorPromptRegistry` does not exist.

- [ ] **Step 3: Implement prompt registry and canonical prompt access**

Create `MemoryAdvisorPromptRegistry` with records:

```java
package com.ainote.app.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class MemoryAdvisorPromptRegistry {

    public PromptMetadata current() {
        return metadataForV2();
    }

    public Optional<PromptMetadata> find(String promptVersion) {
        if (LlmMemorySignalAdvisor.PROMPT_VERSION.equals(promptVersion)) {
            return Optional.of(metadataForV2());
        }
        return Optional.empty();
    }

    public String hashCanonicalTemplate(String template) {
        String normalized = (template == null ? "" : template)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \t]+(?=\n)", "")
                .trim();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private PromptMetadata metadataForV2() {
        List<String> memoryTypes = List.of("fact", "preference", "style", "project_context", "none");
        List<String> signals = List.of(
                "advisor_fact_signal",
                "advisor_preference_signal",
                "advisor_interaction_style_signal",
                "advisor_project_context_signal");
        return new PromptMetadata(
                LlmMemorySignalAdvisor.PROMPT_VERSION,
                "memory-advisor-json-v1",
                hashCanonicalTemplate(LlmMemorySignalAdvisor.canonicalPromptTemplate()),
                memoryTypes,
                signals,
                List.of("deepseek", "openai-compatible"),
                PromptStatus.CANDIDATE);
    }

    public enum PromptStatus {
        CANDIDATE,
        APPROVED,
        DEPRECATED
    }

    public record PromptMetadata(String promptVersion,
                                 String schemaVersion,
                                 String promptHash,
                                 List<String> allowedMemoryTypes,
                                 List<String> allowedSignals,
                                 List<String> compatibleModelFamilies,
                                 PromptStatus status) {
        public PromptMetadata {
            promptVersion = promptVersion == null ? "" : promptVersion;
            schemaVersion = schemaVersion == null ? "" : schemaVersion;
            promptHash = promptHash == null ? "" : promptHash;
            allowedMemoryTypes = allowedMemoryTypes == null ? List.of() : List.copyOf(allowedMemoryTypes);
            allowedSignals = allowedSignals == null ? List.of() : List.copyOf(allowedSignals);
            compatibleModelFamilies = compatibleModelFamilies == null ? List.of() : List.copyOf(compatibleModelFamilies);
            status = status == null ? PromptStatus.CANDIDATE : status;
        }
    }
}
```

Modify `LlmMemorySignalAdvisor`:

```java
static String canonicalPromptTemplate() {
    return systemPromptTemplate() + "\n\n" + userPromptTemplate();
}

private static String systemPromptTemplate() {
    return "Memory advisor prompt version: " + PROMPT_VERSION + ". "
            + "You classify whether a user turn contains stable long-term memory signals. "
            + "Return strict JSON only.";
}

private static String userPromptTemplate() {
    return """
            Decide whether the USER_MESSAGE contains a stable long-term memory signal.

            Safety rules:
            - Never capture RAG or reference_only content.
            - Never capture selected note or selected-note summaries as user profile memory.
            - Never capture one-off instructions such as "this time" or "for this reply".
            - Never capture delete, confirm, cancel, or tool operation requests.
            - Do not capture incidental uses of remember in writing tasks, grammar checks, stories, lyrics, examples, or questions about how to remember something.
            - Capture fact only for stable user/workspace/project facts that the user asks the system to remember, such as "remember that my timezone is UTC+8".
            - Use style for durable instructions about how the assistant should answer, such as tone, format, detail level, language, humor, or summary shape.
            - Use preference for durable product/task preferences that are not answer style.
            - Only capture stable user preference, stable user fact, interaction style, or explicitly labeled project context.

            Return strict JSON with:
            {
              "should_capture": true|false,
              "memory_type": "fact"|"preference"|"style"|"project_context"|"none",
              "confidence": 0.0,
              "signals": ["advisor_fact_signal"|"advisor_preference_signal"|"advisor_interaction_style_signal"|"advisor_project_context_signal"],
              "reason": "short reason"
            }

            USER_MESSAGE:
            %s

            ASSISTANT_OUTPUT:
            %s
            """;
}

private String systemPrompt() {
    return systemPromptTemplate();
}

private String userPrompt(MemoryCapturePolicy.CaptureRequest request) {
    return userPromptTemplate().formatted(
            safe(request == null ? null : request.userMessage()),
            safe(request == null ? null : request.aiResponse()));
}
```

- [ ] **Step 4: Run GREEN**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorPromptRegistryTest,LlmMemorySignalAdvisorTest" test
```

Expected: tests pass.

- [ ] **Step 5: Commit Task 1**

Run:

```powershell
cd D:\ainotetest
git add backend/src/main/java/com/ainote/app/service/MemoryAdvisorPromptRegistry.java backend/src/main/java/com/ainote/app/service/LlmMemorySignalAdvisor.java backend/src/test/java/com/ainote/app/service/MemoryAdvisorPromptRegistryTest.java backend/src/test/java/com/ainote/app/service/LlmMemorySignalAdvisorTest.java
git commit -m "feat: add memory advisor prompt registry"
```

## Task 2: Raw Advisor Result Capture

**Files:**
- Create: `backend/src/main/java/com/ainote/app/service/MemoryAdvisorRawSignalAdvisor.java`
- Create: `backend/src/main/java/com/ainote/app/service/MemoryAdvisorRawResult.java`
- Modify: `backend/src/main/java/com/ainote/app/service/LlmMemorySignalAdvisor.java`
- Test: `backend/src/test/java/com/ainote/app/service/LlmMemorySignalAdvisorTest.java`

- [ ] **Step 1: Write failing raw-result tests**

Add tests to `LlmMemorySignalAdvisorTest`:

```java
@Test
void adviseRawPreservesLowConfidenceCaptureBeforeRuntimeGating() {
    MemoryProperties properties = new MemoryProperties();
    properties.getCapture().getAdvisor().setEnabled(true);
    properties.getCapture().getAdvisor().setMinConfidence(0.82);
    ChatModel chatModel = request -> ChatResponse.builder()
            .aiMessage(AiMessage.from("""
                    {"should_capture":true,"memory_type":"preference","confidence":0.40,
                     "signals":["advisor_preference_signal"],"reason":"weak but parseable"}
                    """))
            .build();
    LlmMemorySignalAdvisor advisor = new LlmMemorySignalAdvisor(properties, chatModel, new ObjectMapper());

    MemoryAdvisorRawResult raw = advisor.adviseRaw(new MemoryCapturePolicy.CaptureRequest(
            "user-1", "I prefer concise answers.", "ok"));

    assertThat(raw.available()).isTrue();
    assertThat(raw.parsed()).isTrue();
    assertThat(raw.rawShouldCapture()).isTrue();
    assertThat(raw.rawMemoryType()).isEqualTo("preference");
    assertThat(raw.rawConfidence()).isEqualTo(0.40);
    assertThat(raw.finalResult().shouldCapture()).isFalse();
    assertThat(raw.finalResult().signals()).contains("advisor_low_confidence");
}

@Test
void adviseDelegatesToFinalGatedRawResult() {
    MemoryProperties properties = new MemoryProperties();
    properties.getCapture().getAdvisor().setEnabled(true);
    properties.getCapture().getAdvisor().setMinConfidence(0.82);
    ChatModel chatModel = request -> ChatResponse.builder()
            .aiMessage(AiMessage.from("""
                    {"should_capture":true,"memory_type":"preference","confidence":0.40,
                     "signals":["advisor_preference_signal"],"reason":"weak but parseable"}
                    """))
            .build();
    LlmMemorySignalAdvisor advisor = new LlmMemorySignalAdvisor(properties, chatModel, new ObjectMapper());

    MemorySignalAdvisor.AdvisorResult result = advisor.advise(new MemoryCapturePolicy.CaptureRequest(
            "user-1", "I prefer concise answers.", "ok"));

    assertThat(result.shouldCapture()).isFalse();
    assertThat(result.signals()).contains("advisor_low_confidence");
}
```

- [ ] **Step 2: Run RED**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=LlmMemorySignalAdvisorTest" test
```

Expected: compilation fails because `MemoryAdvisorRawResult` and `adviseRaw` do not exist.

- [ ] **Step 3: Implement raw advisor records and LLM raw method**

Create `MemoryAdvisorRawSignalAdvisor`:

```java
package com.ainote.app.service;

public interface MemoryAdvisorRawSignalAdvisor extends MemorySignalAdvisor {
    MemoryAdvisorRawResult adviseRaw(MemoryCapturePolicy.CaptureRequest request);
}
```

Create `MemoryAdvisorRawResult`:

```java
package com.ainote.app.service;

import java.util.List;

public record MemoryAdvisorRawResult(boolean available,
                                     boolean parsed,
                                     boolean rawShouldCapture,
                                     String rawMemoryType,
                                     double rawConfidence,
                                     List<String> rawSignals,
                                     String rawReason,
                                     String failureReason,
                                     MemorySignalAdvisor.AdvisorResult finalResult) {
    public MemoryAdvisorRawResult {
        rawMemoryType = rawMemoryType == null ? "none" : rawMemoryType;
        rawSignals = rawSignals == null ? List.of() : List.copyOf(rawSignals);
        rawReason = rawReason == null ? "" : rawReason;
        failureReason = failureReason == null ? "" : failureReason;
        finalResult = finalResult == null
                ? MemorySignalAdvisor.AdvisorResult.unavailable(List.of("advisor_missing_final_result"), "missing final result")
                : finalResult;
    }

    public static MemoryAdvisorRawResult fromFinal(MemorySignalAdvisor.AdvisorResult result) {
        MemorySignalAdvisor.AdvisorResult safe = result == null
                ? MemorySignalAdvisor.AdvisorResult.unavailable(List.of("advisor_null_result"), "null advisor result")
                : result;
        return new MemoryAdvisorRawResult(
                safe.available(),
                safe.available(),
                safe.shouldCapture(),
                safe.memoryType(),
                safe.confidence(),
                safe.signals(),
                safe.reason(),
                safe.available() ? "" : safe.reason(),
                safe);
    }
}
```

Modify `LlmMemorySignalAdvisor` to implement `MemoryAdvisorRawSignalAdvisor`:

```java
@Override
public AdvisorResult advise(MemoryCapturePolicy.CaptureRequest request) {
    return adviseRaw(request).finalResult();
}

@Override
public MemoryAdvisorRawResult adviseRaw(MemoryCapturePolicy.CaptureRequest request) {
    if (!memoryProperties.getCapture().getAdvisor().isEnabled()) {
        AdvisorResult disabled = AdvisorResult.unavailable(List.of("advisor_disabled"), "advisor disabled");
        return MemoryAdvisorRawResult.fromFinal(disabled);
    }
    try {
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from(systemPrompt()),
                        UserMessage.from(userPrompt(request))))
                .build());
        return parseRaw(response.aiMessage().text());
    } catch (Exception e) {
        log.warn("memory_advisor_event=failed error={} message={}",
                e.getClass().getSimpleName(), e.getMessage());
        AdvisorResult unavailable = AdvisorResult.unavailable(List.of("advisor_failed"), e.getClass().getSimpleName());
        return new MemoryAdvisorRawResult(false, false, false, "none", 0.0,
                unavailable.signals(), "", e.getClass().getSimpleName(), unavailable);
    }
}
```

Implement `parseRaw` by moving the existing `parse` logic into a method that first constructs raw fields, then applies the existing confidence gate to produce `finalResult`.

- [ ] **Step 4: Run GREEN**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=LlmMemorySignalAdvisorTest,MemoryAdvisorPromptRegistryTest" test
```

Expected: tests pass.

- [ ] **Step 5: Commit Task 2**

Run:

```powershell
cd D:\ainotetest
git add backend/src/main/java/com/ainote/app/service/MemoryAdvisorRawSignalAdvisor.java backend/src/main/java/com/ainote/app/service/MemoryAdvisorRawResult.java backend/src/main/java/com/ainote/app/service/LlmMemorySignalAdvisor.java backend/src/test/java/com/ainote/app/service/LlmMemorySignalAdvisorTest.java
git commit -m "feat: capture raw memory advisor decisions"
```

## Task 3: Batch Progress Stores Raw Results

**Files:**
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryAdvisorFormalBatchEvaluationService.java`
- Test: `backend/src/test/java/com/ainote/app/service/MemoryAdvisorFormalBatchEvaluationServiceTest.java`

- [ ] **Step 1: Write failing batch raw-result test**

Add to `MemoryAdvisorFormalBatchEvaluationServiceTest`:

```java
@Test
void batchProgressStoresRawResultSeparatelyFromFinalDecision() throws Exception {
    Path progressPath = reportDir.resolve("raw-progress.jsonl");
    Path reportPath = reportDir.resolve("raw-report.json");
    MemoryAdvisorRawSignalAdvisor advisor = new MemoryAdvisorRawSignalAdvisor() {
        @Override
        public MemoryAdvisorRawResult adviseRaw(MemoryCapturePolicy.CaptureRequest request) {
            MemorySignalAdvisor.AdvisorResult finalResult = MemorySignalAdvisor.AdvisorResult.noCapture(
                    "preference",
                    0.40,
                    List.of("advisor_preference_signal", "advisor_low_confidence"),
                    "below runtime threshold");
            return new MemoryAdvisorRawResult(true, true, true, "preference", 0.40,
                    List.of("advisor_preference_signal"), "raw preference", "", finalResult);
        }

        @Override
        public MemorySignalAdvisor.AdvisorResult advise(MemoryCapturePolicy.CaptureRequest request) {
            return adviseRaw(request).finalResult();
        }
    };

    MemoryAdvisorFormalBatchEvaluationService.BatchEvaluationReport report = service.run(
            List.of(replayCase("remember_preference", "remember: I prefer concise answers.", true, "preference")),
            advisor,
            request(progressPath, reportPath, false, 1000, false, 1, 1));

    assertThat(report.results()).hasSize(1);
    MemoryAdvisorFormalBatchEvaluationService.ProgressEntry entry = report.results().get(0);
    assertThat(entry.rawResult().parsed()).isTrue();
    assertThat(entry.rawResult().rawShouldCapture()).isTrue();
    assertThat(entry.rawResult().rawConfidence()).isEqualTo(0.40);
    assertThat(entry.shouldCapture()).isFalse();
    assertThat(Files.readString(progressPath)).contains("\"rawShouldCapture\":true");
}
```

- [ ] **Step 2: Run RED**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorFormalBatchEvaluationServiceTest#batchProgressStoresRawResultSeparatelyFromFinalDecision" test
```

Expected: compilation fails because `ProgressEntry.rawResult()` does not exist.

- [ ] **Step 3: Modify batch evaluator raw capture**

In `evaluateCaseAttempt`, call raw advisors when supported:

```java
Future<MemoryAdvisorRawResult> future = executor.submit(() -> {
    if (advisor instanceof MemoryAdvisorRawSignalAdvisor rawAdvisor) {
        return rawAdvisor.adviseRaw(request);
    }
    return MemoryAdvisorRawResult.fromFinal(advisor.advise(request));
});
```

Update `ProgressEntry` to include:

```java
MemoryAdvisorRawResult rawResult
```

Update the `ProgressEntry.from` factory to accept a raw result, derive final fields from `rawResult.finalResult()`, and persist raw fields in JSON.

- [ ] **Step 4: Run GREEN**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorFormalBatchEvaluationServiceTest" test
```

Expected: tests pass.

- [ ] **Step 5: Commit Task 3**

Run:

```powershell
cd D:\ainotetest
git add backend/src/main/java/com/ainote/app/service/MemoryAdvisorFormalBatchEvaluationService.java backend/src/test/java/com/ainote/app/service/MemoryAdvisorFormalBatchEvaluationServiceTest.java
git commit -m "feat: persist raw advisor results in batch eval"
```

## Task 4: Calibration Service

**Files:**
- Create: `backend/src/main/java/com/ainote/app/service/MemoryAdvisorCalibrationService.java`
- Test: `backend/src/test/java/com/ainote/app/service/MemoryAdvisorCalibrationServiceTest.java`

- [ ] **Step 1: Write failing calibration tests**

Create `MemoryAdvisorCalibrationServiceTest` with tests that:

```java
@Test
void calibrationUsesRawConfidenceAndExcludesUnavailableRows() {
    MemoryAdvisorCalibrationService service = new MemoryAdvisorCalibrationService();
    List<MemoryReplayEvaluationService.MemoryReplayCase> cases = List.of(
            replayCase("positive", true, "preference", "DENY_TRANSIENT"),
            replayCase("negative", false, "", "DENY_TRANSIENT"),
            replayCase("unavailable", true, "preference", "ALLOW_EXPLICIT"));
    List<MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> progress = List.of(
            progress("positive", true, true, true, "preference", 0.70),
            progress("negative", true, true, false, "none", 0.10),
            unavailableProgress("unavailable"));

    MemoryAdvisorCalibrationService.CalibrationReport report = service.calibrate(
            cases,
            progress,
            0.82,
            MemoryAdvisorProductionQualityService.AdvisorQualityThresholds.productionDefaults());

    assertThat(report.evaluatedCases()).isEqualTo(3);
    assertThat(report.calibratedCases()).isEqualTo(2);
    assertThat(report.excludedUnavailableCases()).isEqualTo(1);
    assertThat(report.candidates()).extracting(MemoryAdvisorCalibrationService.ThresholdCandidate::threshold)
            .contains(0.70);
    assertThat(report.recommendedThreshold()).isEqualTo(0.50);
}
```

The helper methods in the test create replay cases and progress entries with raw results.

- [ ] **Step 2: Run RED**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorCalibrationServiceTest" test
```

Expected: compilation fails because `MemoryAdvisorCalibrationService` does not exist.

- [ ] **Step 3: Implement calibration service**

Create records:

```java
public record CalibrationReport(int evaluatedCases,
                                int calibratedCases,
                                int positiveCases,
                                int negativeCases,
                                int excludedUnavailableCases,
                                int excludedParseErrorCases,
                                int excludedOtherErrorCases,
                                double currentMinConfidence,
                                double recommendedThreshold,
                                boolean deployableThresholdFound,
                                String rationale,
                                List<ThresholdCandidate> candidates) {
}

public record ThresholdCandidate(double threshold,
                                 double captureDecisionAccuracy,
                                 double falsePositiveRate,
                                 double falseNegativeRate,
                                 double memoryTypeAccuracy,
                                 double sensitiveFalseAllowRate,
                                 boolean productionThresholdsPassed) {
}
```

Candidate thresholds are exactly:

```java
List.of(0.50, 0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.90, 0.95)
```

For each parsed available raw result, candidate `shouldCapture` is:

```java
raw.rawShouldCapture()
        && raw.rawConfidence() >= threshold
        && !"none".equals(raw.rawMemoryType())
```

- [ ] **Step 4: Run GREEN**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorCalibrationServiceTest" test
```

Expected: tests pass.

- [ ] **Step 5: Commit Task 4**

Run:

```powershell
cd D:\ainotetest
git add backend/src/main/java/com/ainote/app/service/MemoryAdvisorCalibrationService.java backend/src/test/java/com/ainote/app/service/MemoryAdvisorCalibrationServiceTest.java
git commit -m "feat: add memory advisor threshold calibration"
```

## Task 5: A/B Comparison And Failure Metrics

**Files:**
- Create: `backend/src/main/java/com/ainote/app/service/MemoryAdvisorAbComparisonService.java`
- Create: `backend/src/main/java/com/ainote/app/service/MemoryAdvisorFailureMetricsService.java`
- Test: `backend/src/test/java/com/ainote/app/service/MemoryAdvisorAbComparisonServiceTest.java`
- Test: `backend/src/test/java/com/ainote/app/service/MemoryAdvisorFailureMetricsServiceTest.java`

- [ ] **Step 1: Write failing A/B comparison test**

Create `MemoryAdvisorAbComparisonServiceTest`:

```java
@Test
void comparesAdvisorAgainstRulePolicyBaseline() {
    MemoryAdvisorAbComparisonService service = new MemoryAdvisorAbComparisonService(
            new MemoryCapturePolicy(),
            new MemoryCandidateExtractor());
    List<MemoryReplayEvaluationService.MemoryReplayCase> cases = List.of(
            replayCase("allow", "remember: I prefer concise answers.", true, "ALLOW_EXPLICIT", "preference"),
            replayCase("deny", "summarize this for this reply only", false, "DENY_TRANSIENT", ""));
    List<MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> progress = List.of(
            progress("allow", true, "preference"),
            progress("deny", true, "preference"));

    MemoryAdvisorAbComparisonService.AbComparisonReport report = service.compare(cases, progress);

    assertThat(report.totalCases()).isEqualTo(2);
    assertThat(report.bothAllow()).isEqualTo(1);
    assertThat(report.advisorOnlyAllow()).isEqualTo(1);
    assertThat(report.ruleOnlyAllow()).isZero();
    assertThat(report.advisorRegressions()).extracting(MemoryAdvisorAbComparisonService.AbCaseSample::caseId)
            .contains("deny");
}
```

- [ ] **Step 2: Write failing failure metrics test**

Create `MemoryAdvisorFailureMetricsServiceTest`:

```java
@Test
void summarizesAvailabilityRetryReasonsAndLatency() {
    MemoryAdvisorFailureMetricsService service = new MemoryAdvisorFailureMetricsService();
    List<MemoryAdvisorFormalBatchEvaluationService.ProgressEntry> progress = List.of(
            progress("ok", COMPLETED, true, 10, 1, "ok"),
            progress("timeout", TIMEOUT, false, 200, 3, "TIMEOUT"),
            progress("error", ERROR, false, 50, 2, "IOException"));

    MemoryAdvisorFailureMetricsService.FailureMetricsReport report = service.summarize(progress);

    assertThat(report.totalEvaluatedCases()).isEqualTo(3);
    assertThat(report.availableCases()).isEqualTo(1);
    assertThat(report.unavailableCases()).isEqualTo(2);
    assertThat(report.timeoutCases()).isEqualTo(1);
    assertThat(report.errorCases()).isEqualTo(1);
    assertThat(report.retryAttemptCount()).isEqualTo(6);
    assertThat(report.retriedCaseCount()).isEqualTo(2);
    assertThat(report.p95LatencyMillis()).isEqualTo(200);
    assertThat(report.topUnavailableReasons()).extracting(MemoryAdvisorFailureMetricsService.ReasonCount::reason)
            .contains("TIMEOUT", "IOException");
}
```

- [ ] **Step 3: Run RED**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorAbComparisonServiceTest,MemoryAdvisorFailureMetricsServiceTest" test
```

Expected: compilation fails because both services do not exist.

- [ ] **Step 4: Implement A/B and failure metrics services**

Implement `MemoryAdvisorAbComparisonService.compare` by mapping progress by case ID and evaluating the same cases through `MemoryCapturePolicy`.

Implement `MemoryAdvisorFailureMetricsService.summarize` using `ProgressEntry.status`, `available`, `attemptCount`, `reason`, and `latencyMillis`.

- [ ] **Step 5: Run GREEN**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorAbComparisonServiceTest,MemoryAdvisorFailureMetricsServiceTest" test
```

Expected: tests pass.

- [ ] **Step 6: Commit Task 5**

Run:

```powershell
cd D:\ainotetest
git add backend/src/main/java/com/ainote/app/service/MemoryAdvisorAbComparisonService.java backend/src/main/java/com/ainote/app/service/MemoryAdvisorFailureMetricsService.java backend/src/test/java/com/ainote/app/service/MemoryAdvisorAbComparisonServiceTest.java backend/src/test/java/com/ainote/app/service/MemoryAdvisorFailureMetricsServiceTest.java
git commit -m "feat: add advisor ab comparison and failure metrics"
```

## Task 6: Release Gate Service

**Files:**
- Create: `backend/src/main/java/com/ainote/app/service/MemoryAdvisorReleaseGateService.java`
- Test: `backend/src/test/java/com/ainote/app/service/MemoryAdvisorReleaseGateServiceTest.java`

- [ ] **Step 1: Write failing release gate tests**

Create `MemoryAdvisorReleaseGateServiceTest` with tests:

```java
@Test
void staleV1PromptBlocksRelease() {
    MemoryAdvisorReleaseGateService service = service(168);
    MemoryAdvisorReleaseGateService.ReleaseGateDecision decision = service.evaluate(packageWithPrompt("memory-advisor-v1"));

    assertThat(decision.status()).isEqualTo(MemoryAdvisorReleaseGateService.ReleaseGateStatus.BLOCKED);
    assertThat(decision.blockReasons()).contains("prompt_version_unknown");
}

@Test
void bareReadinessReportBlocksRelease() {
    MemoryAdvisorReleaseGateService service = service(168);
    MemoryAdvisorReleaseGateService.ReleaseGateDecision decision = service.evaluateReadinessOnly(readinessPassing());

    assertThat(decision.status()).isEqualTo(MemoryAdvisorReleaseGateService.ReleaseGateStatus.BLOCKED);
    assertThat(decision.blockReasons()).contains("missing_release_readiness_package");
}

@Test
void candidatePromptCanPassWithApprovalActionRequired() {
    MemoryAdvisorReleaseGateService service = service(168);
    MemoryAdvisorReleaseGateService.ReleaseGateDecision decision = service.evaluate(completePassingPackage());

    assertThat(decision.status()).isEqualTo(MemoryAdvisorReleaseGateService.ReleaseGateStatus.PASS);
    assertThat(decision.promptStatus()).isEqualTo(MemoryAdvisorPromptRegistry.PromptStatus.CANDIDATE);
    assertThat(decision.approvalActionRequired()).isEqualTo("promote_prompt_to_approved");
}

@Test
void staleReportBlocksReleaseReadiness() {
    MemoryAdvisorReleaseGateService service = service(168);
    MemoryAdvisorReleaseGateService.MemoryAdvisorReleaseReadinessPackage releasePackage =
            completePassingPackageWithCompletedAt("2020-01-01T00:00:00Z");

    MemoryAdvisorReleaseGateService.ReleaseGateDecision decision = service.evaluate(releasePackage);

    assertThat(decision.status()).isEqualTo(MemoryAdvisorReleaseGateService.ReleaseGateStatus.BLOCKED);
    assertThat(decision.blockReasons()).contains("report_stale");
}
```

- [ ] **Step 2: Run RED**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorReleaseGateServiceTest" test
```

Expected: compilation fails because `MemoryAdvisorReleaseGateService` does not exist.

- [ ] **Step 3: Implement release gate service**

Create `MemoryAdvisorReleaseGateService` with records:

```java
public record MemoryAdvisorReleaseReadinessPackage(String runId,
                                                   String datasetVersion,
                                                   String modelName,
                                                   String promptVersion,
                                                   String completedAt,
                                                   String progressPath,
                                                   String batchReportPath,
                                                   MemoryAdvisorPromptRegistry.PromptMetadata promptMetadata,
                                                   MemoryAdvisorProductionQualityService.AdvisorReadinessReport readinessReport,
                                                   MemoryAdvisorCalibrationService.CalibrationReport calibrationReport,
                                                   MemoryAdvisorAbComparisonService.AbComparisonReport abComparisonReport,
                                                   MemoryAdvisorFailureMetricsService.FailureMetricsReport failureMetricsReport) {
}

public record ReleaseGateDecision(ReleaseGateStatus status,
                                  String runId,
                                  String datasetVersion,
                                  String modelName,
                                  String promptVersion,
                                  String promptHash,
                                  MemoryAdvisorPromptRegistry.PromptStatus promptStatus,
                                  String approvalActionRequired,
                                  int evaluatedCases,
                                  boolean qualityGatePassed,
                                  List<String> blockReasons,
                                  String generatedAt,
                                  String progressPath,
                                  String batchReportPath) {
}
```

The service blocks on missing package, unknown/deprecated prompt, stale prompt version, hash mismatch, missing calibration, missing A/B comparison, missing failure metrics, failed readiness gate, no deployable threshold, advisor regression, sensitive false allow, availability below threshold, timeout or error rate above threshold, invalid timestamp, or stale report.

- [ ] **Step 4: Run GREEN**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorReleaseGateServiceTest" test
```

Expected: tests pass.

- [ ] **Step 5: Commit Task 6**

Run:

```powershell
cd D:\ainotetest
git add backend/src/main/java/com/ainote/app/service/MemoryAdvisorReleaseGateService.java backend/src/test/java/com/ainote/app/service/MemoryAdvisorReleaseGateServiceTest.java
git commit -m "feat: add memory advisor release gate"
```

## Task 7: Integrate Release Package Into Formal Batch Report

**Files:**
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryAdvisorFormalBatchEvaluationService.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryAdvisorFormalBatchEvaluationServiceTest.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryAdvisorFormalEvaluationIT.java`
- Modify: `backend/src/main/resources/application.yml.example`

- [ ] **Step 1: Write failing integration test**

Add to `MemoryAdvisorFormalBatchEvaluationServiceTest`:

```java
@Test
void completedBatchReportContainsReleaseReadinessPackage() throws Exception {
    Path progressPath = reportDir.resolve("release-progress.jsonl");
    Path reportPath = reportDir.resolve("release-report.json");

    MemoryAdvisorFormalBatchEvaluationService.BatchEvaluationReport report = service.run(
            replayCases(),
            matchingAdvisor(new AtomicInteger()),
            request(progressPath, reportPath, false, 1000));

    assertThat(report.releaseReadinessPackage()).isNotNull();
    assertThat(report.releaseGateDecision()).isNotNull();
    assertThat(report.releaseReadinessPackage().promptMetadata().promptVersion())
            .isEqualTo(LlmMemorySignalAdvisor.PROMPT_VERSION);
    assertThat(report.releaseReadinessPackage().calibrationReport()).isNotNull();
    assertThat(report.releaseReadinessPackage().abComparisonReport()).isNotNull();
    assertThat(report.releaseReadinessPackage().failureMetricsReport()).isNotNull();

    String json = Files.readString(reportPath);
    assertThat(json)
            .contains("\"releaseReadinessPackage\"")
            .contains("\"releaseGateDecision\"")
            .contains("\"promptHash\"")
            .contains("\"recommendedThreshold\"");
}
```

- [ ] **Step 2: Run RED**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorFormalBatchEvaluationServiceTest#completedBatchReportContainsReleaseReadinessPackage" test
```

Expected: compilation fails because `releaseReadinessPackage` and `releaseGateDecision` are missing.

- [ ] **Step 3: Wire new services into batch evaluation**

Extend `MemoryAdvisorFormalBatchEvaluationService` constructor to accept:

```java
MemoryAdvisorPromptRegistry promptRegistry,
MemoryAdvisorCalibrationService calibrationService,
MemoryAdvisorAbComparisonService abComparisonService,
MemoryAdvisorFailureMetricsService failureMetricsService,
MemoryAdvisorReleaseGateService releaseGateService
```

Preserve the existing constructor by delegating to the extended constructor with default service instances.

After readiness report creation, build:

```java
PromptMetadata promptMetadata = promptRegistry.find(safeRequest.formalRequest().promptVersion()).orElse(null);
CalibrationReport calibrationReport = calibrationService.calibrate(orderedProgress);
AbComparisonReport abComparisonReport = abComparisonService.compare(safeRequest, orderedProgress);
FailureMetricsReport failureMetricsReport = failureMetricsService.summarize(orderedProgress);
MemoryAdvisorReleaseReadinessPackage releasePackage = MemoryAdvisorReleaseReadinessPackage.from(
        readinessReport,
        promptMetadata,
        calibrationReport,
        abComparisonReport,
        failureMetricsReport);
ReleaseGateDecision releaseGateDecision = releaseGateService.evaluate(releasePackage);
```

Add both objects to `BatchEvaluationReport`.

- [ ] **Step 4: Document freshness env var**

Add this line to `backend/src/main/resources/application.yml.example`:

```yaml
#   MEMORY_ADVISOR_RELEASE_GATE_MAX_REPORT_AGE_HOURS - Defaults to 168
```

- [ ] **Step 5: Run GREEN**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorFormalBatchEvaluationServiceTest,MemoryAdvisorFormalEvaluationIT" test
```

Expected: batch tests pass; `MemoryAdvisorFormalEvaluationIT` is skipped unless the enable flag is set.

- [ ] **Step 6: Commit Task 7**

Run:

```powershell
cd D:\ainotetest
git add backend/src/main/java/com/ainote/app/service/MemoryAdvisorFormalBatchEvaluationService.java backend/src/test/java/com/ainote/app/service/MemoryAdvisorFormalBatchEvaluationServiceTest.java backend/src/test/java/com/ainote/app/service/MemoryAdvisorFormalEvaluationIT.java backend/src/main/resources/application.yml.example
git commit -m "feat: package memory advisor release readiness"
```

## Task 8: Verification And Optional V2 Formal Run

**Files:**
- No new source files.
- Generated reports stay under `backend/target/memory-advisor-formal-eval/` and are not committed.

- [ ] **Step 1: Run closed-loop focused tests**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryAdvisorPromptRegistryTest,LlmMemorySignalAdvisorTest,MemoryAdvisorFormalBatchEvaluationServiceTest,MemoryAdvisorCalibrationServiceTest,MemoryAdvisorAbComparisonServiceTest,MemoryAdvisorFailureMetricsServiceTest,MemoryAdvisorReleaseGateServiceTest,MemoryAdvisorProductionQualityServiceTest,MemoryAdvisorFormalEvaluationServiceTest" test
```

Expected: build success, 0 failures, 0 errors.

- [ ] **Step 2: Run full backend tests**

Run:

```powershell
cd D:\ainotetest\backend
mvn test
```

Expected: build success, 0 failures, 0 errors.

- [ ] **Step 3: Run diff checks**

Run:

```powershell
cd D:\ainotetest
git diff --check
git status --short --branch
```

Expected: no whitespace errors; only known unrelated untracked `.superpowers/` and July 3 docs remain outside the task.

- [ ] **Step 4: Confirm no verification-only edits remain**

If verification reveals a documentation mismatch, stop and explicitly review the needed correction before creating any additional documentation commit.

- [ ] **Step 5: Run v2 formal evaluation when API credentials are available**

Run only when `DEEPSEEK_API_KEY` is configured:

```powershell
cd D:\ainotetest\backend
$env:MEMORY_ADVISOR_FORMAL_EVAL_ENABLED="true"
$env:DEEPSEEK_API_KEY=$env:DEEPSEEK_API_KEY
$env:MEMORY_ADVISOR_FORMAL_EVAL_RUN_ID="memory-advisor-v2-formal-eval-4000"
$env:MEMORY_ADVISOR_FORMAL_EVAL_MAX_CASES="4000"
$env:MEMORY_ADVISOR_FORMAL_EVAL_MIN_CASES="4000"
$env:MEMORY_ADVISOR_FORMAL_EVAL_MAX_COST_YUAN="20"
$env:MEMORY_ADVISOR_FORMAL_EVAL_RETRY_UNAVAILABLE_PROGRESS="true"
$env:MEMORY_ADVISOR_FORMAL_EVAL_MAX_ATTEMPTS="3"
mvn "-Dtest=MemoryAdvisorFormalEvaluationIT" test
```

Expected when API credentials are available: a v2 report is written under `target/memory-advisor-formal-eval/`, includes `promptVersion=memory-advisor-v2`, includes `releaseReadinessPackage`, and has a release gate decision.

Expected when API credentials are unavailable: do not run this command; final status must say real-model tuning is pending API-backed run.

- [ ] **Step 6: Final implementation commit check**

Run:

```powershell
cd D:\ainotetest
git log --oneline -8
git status --short --branch
```

Expected: task commits are present, and unrelated untracked files remain uncommitted.
