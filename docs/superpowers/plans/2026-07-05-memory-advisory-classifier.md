# Memory Advisory Classifier Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a feature-flagged advisory classifier that can add auditable allow-side memory signals without overriding hard deny rules.

**Architecture:** `MemorySignalClassifier` continues deterministic signal detection, skips advisory classification when hard deny signals exist, and merges high-confidence advisory signals only when enabled. `MemoryCapturePolicy` remains the final decision boundary.

**Tech Stack:** Spring Boot configuration properties, Java records/interfaces, LangChain4j `ChatModel`, Jackson `ObjectMapper`, JUnit 5, Mockito.

---

## File Structure

- Modify: `backend/src/main/java/com/ainote/app/config/MemoryProperties.java`
  - Add `capture.advisor.enabled` and `capture.advisor.minConfidence`.
- Create: `backend/src/main/java/com/ainote/app/service/MemorySignalAdvisor.java`
  - Interface and records for advisory classification.
- Create: `backend/src/main/java/com/ainote/app/service/LlmMemorySignalAdvisor.java`
  - Feature-flagged ChatModel-backed advisor.
- Modify: `backend/src/main/java/com/ainote/app/service/MemorySignalClassifier.java`
  - Inject advisor, skip it for hard deny inputs, merge safe high-confidence advice.
- Modify: `backend/src/test/java/com/ainote/app/config/MemoryPropertiesTest.java`
  - Cover default and bound advisor properties.
- Create: `backend/src/test/java/com/ainote/app/service/LlmMemorySignalAdvisorTest.java`
  - Cover disabled mode, parsing, low confidence, malformed output, and prompt safety.
- Modify: `backend/src/test/java/com/ainote/app/service/MemorySignalClassifierTest.java`
  - Cover advisor merge and hard-deny skip.
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryCapturePolicyTest.java`
  - Cover an advisor-only stable preference path.

---

### Task 1: RED Tests For Config And Advisor Boundary

- [ ] Add `MemoryPropertiesTest` assertions:
  - default advisor disabled
  - default min confidence `0.82`
  - binder supports `app.memory.capture.advisor.enabled=true`
  - binder supports `app.memory.capture.advisor.min-confidence=0.9`

- [ ] Create `LlmMemorySignalAdvisorTest` with mocked `ChatModel`:
  - disabled advisor returns unavailable and does not call model
  - enabled advisor parses valid JSON
  - low confidence result is marked not capturable
  - malformed JSON returns unavailable with `advisor_failed`
  - prompt contains instruction not to capture RAG/selected-note/reference-only content

- [ ] Modify `MemorySignalClassifierTest`:
  - hard deny selected-note input does not call advisor
  - high-confidence advisor signal adds `advisor_preference_signal`
  - advisor failure adds `advisor_failed` and does not allow-side classify

- [ ] Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryPropertiesTest,LlmMemorySignalAdvisorTest,MemorySignalClassifierTest,MemoryCapturePolicyTest" test
```

Expected: fail before implementation because advisor classes and properties do not exist.

### Task 2: Implement Configuration And Advisor

- [ ] Add `MemoryProperties.Capture.Advisor` nested class with:
  - `enabled = false`
  - `minConfidence = 0.82`

- [ ] Add `MemorySignalAdvisor` interface:
  - `AdvisorResult advise(MemoryCapturePolicy.CaptureRequest request)`
  - result fields: `available`, `shouldCapture`, `memoryType`, `confidence`, `signals`, `reason`

- [ ] Add `LlmMemorySignalAdvisor`:
  - Uses `MemoryProperties`, `ChatModel`, and `ObjectMapper`
  - Returns unavailable when disabled
  - Calls model only when enabled
  - Parses strict JSON
  - Filters signals to allowlist
  - Requires `confidence >= minConfidence`
  - Catches exceptions and returns unavailable with `advisor_failed`

### Task 3: Merge Advisor Signals Safely

- [ ] Add constructor injection to `MemorySignalClassifier`.
- [ ] Keep package-private no-arg constructor for unit tests.
- [ ] Determine hard deny before advisor call.
- [ ] Skip advisor when hard deny is present.
- [ ] Merge advisor signals only if:
  - advisor result is available
  - `shouldCapture=true`
  - confidence passes threshold inside advisor
  - memory type is one of `preference`, `style`, `project_context`
- [ ] Map advisor memory type:
  - `preference` -> `preferenceSignal=true`
  - `style` -> `interactionStyleSignal=true`, `preferenceSignal=true`
  - `project_context` -> `projectContextSignal=true`

### Task 4: Verification

- [ ] Run focused tests:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryPropertiesTest,LlmMemorySignalAdvisorTest,MemorySignalClassifierTest,MemoryCapturePolicyTest" test
```

- [ ] Run memory regression:

```powershell
mvn "-Dtest=MemoryCapturePolicyTest,MemorySignalClassifierTest,LlmMemorySignalAdvisorTest,MemoryCandidateExtractorTest,MemorySystemEvaluationTest,MemoryWriteServiceTest,MemoryOrchestratorTest,MemoryExtractionServiceTest,MemoryRetrievalServiceTest,ContextAssemblerTest" test
```

- [ ] Run full backend:

```powershell
mvn test
```

- [ ] Run whitespace check:

```powershell
cd D:\ainotetest
git diff --check
```

### Task 5: Runtime And Commit

- [ ] Restart backend and verify `/actuator/health`.
- [ ] Commit only files listed in this plan.
- [ ] Report modified files, behavior, tests, risks, and manual verification.

## Self-Review

- No database migration is required.
- Advisor is disabled by default.
- Hard deny priority remains above advisor.
- The LLM output cannot directly create a memory.
- Existing eval cases remain mandatory.
