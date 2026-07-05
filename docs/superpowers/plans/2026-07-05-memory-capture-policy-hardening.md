# Memory Capture Policy Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make long-term memory capture handle stable interaction-style corrections such as "现在不要这么俏皮，要严肃深刻" while preserving the governed boundary that transient commands, RAG content, selected-note content, destructive operations, confirmations, and sensitive data must not become long-term memory.

**Architecture:** Keep the existing governed memory pipeline: `MemoryCapturePolicy` decides whether a user turn may become long-term memory, `MemoryCandidateExtractor` normalizes allowed content into one candidate, and `MemoryWriteService` writes provenance and events. This plan improves rule quality inside the existing boundary instead of adding an LLM write path or bypassing the event ledger.

**Tech Stack:** Spring Boot, JUnit 5, AssertJ, existing memory governance services, PostgreSQL-backed event ledger.

---

## File Structure

- Modify: `backend/src/main/java/com/ainote/app/service/MemoryCapturePolicy.java`
  - Responsibility: decide whether a turn is stable memory material and return an auditable reason.
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryCandidateExtractor.java`
  - Responsibility: normalize allowed memory content into a durable semantic memory candidate.
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryCapturePolicyTest.java`
  - Responsibility: policy-level positive and negative behavior tests.
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryCandidateExtractorTest.java`
  - Responsibility: extraction and normalization tests.
- Modify: `backend/src/test/resources/memory/eval-cases.json`
  - Responsibility: reusable governance evaluation cases.
- Modify if needed: `backend/src/test/java/com/ainote/app/service/MemorySystemEvaluationTest.java`
  - Responsibility: assert eval cases are present and match policy/extractor behavior.

---

### Task 1: Reproduce the Missing Stable Style Correction

**Files:**
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryCapturePolicyTest.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryCandidateExtractorTest.java`
- Modify: `backend/src/test/resources/memory/eval-cases.json`

- [ ] **Step 1: Write the failing policy test**

Add this test to `MemoryCapturePolicyTest`:

```java
@Test
void styleCorrectionWithoutRememberKeywordShouldBeAllowed() {
    MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(
            new MemoryCapturePolicy.CaptureRequest(
                    "user-1",
                    "现在不要这么俏皮，要严肃深刻",
                    "收到，切换模式。"));

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.type()).isEqualTo(MemoryCapturePolicy.DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE);
    assertThat(decision.reason()).contains("correction");
}
```

- [ ] **Step 2: Write the failing extractor test**

Add this test to `MemoryCandidateExtractorTest`:

```java
@Test
void extractsImplicitStyleCorrectionCandidate() {
    MemoryCapturePolicy.CaptureRequest request = new MemoryCapturePolicy.CaptureRequest(
            "user-1",
            "现在不要这么俏皮，要严肃深刻",
            "收到，切换模式。");
    MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request);

    List<MemoryCandidateExtractor.MemoryCandidate> candidates = extractor.extract(request, decision);

    assertThat(candidates).hasSize(1);
    assertThat(candidates.get(0).category()).isEqualTo("preference");
    assertThat(candidates.get(0).correction()).isTrue();
    assertThat(candidates.get(0).content()).contains("严肃深刻");
}
```

- [ ] **Step 3: Add the eval case**

Add this object to `backend/src/test/resources/memory/eval-cases.json` after `should_supersede_corrected_preference`:

```json
{
  "id": "should_capture_interaction_style_correction",
  "userMessage": "现在不要这么俏皮，要严肃深刻",
  "assistantOutput": "收到，切换模式。",
  "expectedCaptureAllowed": true,
  "expectedDecisionType": "ALLOW_IMPLICIT_LOW_CONFIDENCE",
  "expectedMemoryType": "preference",
  "expectedCorrection": true
}
```

- [ ] **Step 4: Run RED verification**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryCapturePolicyTest,MemoryCandidateExtractorTest,MemorySystemEvaluationTest" test
```

Expected before implementation: FAIL with `no_stable_user_memory_signal` and no extracted candidates.

---

### Task 2: Add False-Positive Protection Tests

**Files:**
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryCapturePolicyTest.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryCandidateExtractorTest.java`
- Modify: `backend/src/test/resources/memory/eval-cases.json`

- [ ] **Step 1: Test that one-off style instructions are denied**

Add this test to `MemoryCapturePolicyTest`:

```java
@Test
void oneOffStyleInstructionShouldNotBecomeLongTermMemory() {
    MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(
            new MemoryCapturePolicy.CaptureRequest(
                    "user-1",
                    "这次回答不要这么俏皮，要严肃深刻",
                    "好的，这次我会严肃一些。"));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).contains("one_off");
}
```

- [ ] **Step 2: Test that negative-only style commands are denied**

Add this test to `MemoryCapturePolicyTest`:

```java
@Test
void negativeOnlyStyleInstructionShouldNotBecomeLongTermMemory() {
    MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(
            new MemoryCapturePolicy.CaptureRequest(
                    "user-1",
                    "不要这么俏皮",
                    "好的。"));

    assertThat(decision.allowed()).isFalse();
}
```

- [ ] **Step 3: Test normalized style memory text**

Extend `extractsImplicitStyleCorrectionCandidate` in `MemoryCandidateExtractorTest`:

```java
assertThat(candidates.get(0).content()).isEqualTo("希望交互风格严肃深刻，避免俏皮");
```

- [ ] **Step 4: Add eval false-positive case**

Add this object to `eval-cases.json`:

```json
{
  "id": "should_not_capture_one_off_style_instruction",
  "userMessage": "这次回答不要这么俏皮，要严肃深刻",
  "assistantOutput": "好的，这次我会严肃一些。",
  "expectedCaptureAllowed": false,
  "expectedDecisionType": "DENY_TRANSIENT"
}
```

- [ ] **Step 5: Run RED verification**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryCapturePolicyTest,MemoryCandidateExtractorTest,MemorySystemEvaluationTest" test
```

Expected before final implementation: one-off and normalization tests fail until policy and extractor are tightened.

---

### Task 3: Implement Structured Style-Correction Policy

**Files:**
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryCapturePolicy.java`

- [ ] **Step 1: Add auditable style signal detection**

Add constants near `SENSITIVE_PATTERN`:

```java
private static final Pattern INTERACTION_STYLE_SIGNAL = Pattern.compile(
        "(回答|回复|语气|口吻|风格|格式|详细|简短|严肃|深刻|正式|专业|俏皮|轻松|幽默|活泼|啰嗦|精简|简洁|"
                + "answer|reply|tone|style|format|formal|serious|concise|detailed|professional)");
```

- [ ] **Step 2: Deny one-off instructions before implicit correction rules**

Add this check after `isTransientOperation(compact)` and before `isExplicitRemember(compact)`:

```java
if (isOneOffInstruction(compact)) {
    return CaptureDecision.deny(DecisionType.DENY_TRANSIENT, "one_off_instruction");
}
```

Add helper:

```java
private boolean isOneOffInstruction(String compact) {
    return compact.contains("这次")
            || compact.contains("本次")
            || compact.contains("当前这次")
            || compact.contains("thisonce")
            || compact.contains("thisreply")
            || compact.contains("forthisreply");
}
```

- [ ] **Step 3: Add structured contrastive style correction**

Add this check after `looksLikePreferenceCorrection(compact)`:

```java
if (looksLikeInteractionStyleCorrection(userMessage, compact)) {
    return CaptureDecision.allow(
            DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE,
            "correction_interaction_style",
            0.85);
}
```

Add helper:

```java
private boolean looksLikeInteractionStyleCorrection(String userMessage, String compact) {
    boolean hasNegativeDirective = compact.contains("不要")
            || compact.contains("别")
            || compact.contains("少一点")
            || compact.contains("少点")
            || compact.contains("not")
            || compact.contains("less");
    boolean hasPositiveReplacement = compact.contains("改成")
            || compact.contains("改为")
            || compact.contains("多一点")
            || compact.contains("多点")
            || compact.contains("instead")
            || hasStandaloneChineseWantAfterNegativeDirective(compact)
            || compact.contains("more");
    return hasNegativeDirective
            && hasPositiveReplacement
            && INTERACTION_STYLE_SIGNAL.matcher(userMessage).find();
}
```

Add helper:

```java
private boolean hasStandaloneChineseWantAfterNegativeDirective(String compact) {
    int negativeIndex = firstNonNegativeDirectiveIndex(compact);
    if (negativeIndex < 0) {
        return false;
    }
    int wantIndex = compact.indexOf("要", negativeIndex + 1);
    while (wantIndex >= 0) {
        boolean partOfNegative = wantIndex > 0 && compact.charAt(wantIndex - 1) == '不';
        if (!partOfNegative) {
            return true;
        }
        wantIndex = compact.indexOf("要", wantIndex + 1);
    }
    return false;
}

private int firstNonNegativeDirectiveIndex(String compact) {
    int best = -1;
    for (String token : List.of("不要", "别", "少一点", "少点")) {
        int index = compact.indexOf(token);
        if (index >= 0 && (best < 0 || index < best)) {
            best = index;
        }
    }
    return best;
}
```

Import `java.util.List`.

- [ ] **Step 4: Run policy tests**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryCapturePolicyTest" test
```

Expected: PASS.

---

### Task 4: Implement Durable Candidate Normalization

**Files:**
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryCandidateExtractor.java`

- [ ] **Step 1: Add a contrastive Chinese style pattern**

Add near class top:

```java
private static final Pattern CHINESE_CONTRASTIVE_STYLE = Pattern.compile(
        "^(?:现在|以后|接下来|之后)?(?:请)?(?:不要|别|少一点|少点)(?<avoid>[^，,。；;]+)[，,。；;]?(?:要|改成|改为|多一点|多点)(?<prefer>[^，,。；;]+)$");
```

Import:

```java
import java.util.regex.Matcher;
import java.util.regex.Pattern;
```

- [ ] **Step 2: Normalize contrastive style before generic normalization**

At the start of `normalizeContent`:

```java
String contrastiveStyle = normalizeContrastiveStyle(userMessage);
if (!contrastiveStyle.isBlank()) {
    return contrastiveStyle;
}
```

Add helpers:

```java
private String normalizeContrastiveStyle(String userMessage) {
    String normalized = userMessage == null ? "" : userMessage.trim();
    Matcher matcher = CHINESE_CONTRASTIVE_STYLE.matcher(normalized);
    if (!matcher.find()) {
        return "";
    }

    String avoid = cleanupStyleFragment(matcher.group("avoid"));
    String prefer = cleanupStyleFragment(matcher.group("prefer"));
    if (avoid.isBlank() || prefer.isBlank()) {
        return "";
    }
    return "希望交互风格" + prefer + "，避免" + avoid;
}

private String cleanupStyleFragment(String value) {
    return value == null ? "" : value
            .replaceFirst("^这么", "")
            .replaceFirst("^太", "")
            .trim();
}
```

- [ ] **Step 3: Mark contrastive style as correction**

In `isCorrection`, add:

```java
|| isContrastiveCorrection(text)
```

Add helper:

```java
private boolean isContrastiveCorrection(String text) {
    return CHINESE_CONTRASTIVE_STYLE.matcher(text == null ? "" : text.trim()).find();
}
```

- [ ] **Step 4: Run extractor tests**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryCandidateExtractorTest" test
```

Expected: PASS.

---

### Task 5: Verify Governance Regression Suite

**Files:**
- No production edits expected.

- [ ] **Step 1: Run focused governance tests**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryCapturePolicyTest,MemoryCandidateExtractorTest,MemorySystemEvaluationTest,MemoryWriteServiceTest,MemoryOrchestratorTest,MemoryExtractionServiceTest,ContextAssemblerTest" test
```

Expected: PASS with zero failures.

- [ ] **Step 2: Run full backend tests**

Run:

```powershell
cd D:\ainotetest\backend
mvn test
```

Expected: PASS with zero failures.

- [ ] **Step 3: Check whitespace**

Run:

```powershell
cd D:\ainotetest
git diff --check
```

Expected: no whitespace errors. CRLF warnings are acceptable on this Windows workspace.

---

### Task 6: Commit and Runtime Verification

**Files:**
- Commit only the files listed in the File Structure section plus this plan.

- [ ] **Step 1: Stage implementation files**

Run:

```powershell
cd D:\ainotetest
git add docs/superpowers/plans/2026-07-05-memory-capture-policy-hardening.md `
  backend/src/main/java/com/ainote/app/service/MemoryCapturePolicy.java `
  backend/src/main/java/com/ainote/app/service/MemoryCandidateExtractor.java `
  backend/src/test/java/com/ainote/app/service/MemoryCapturePolicyTest.java `
  backend/src/test/java/com/ainote/app/service/MemoryCandidateExtractorTest.java `
  backend/src/test/resources/memory/eval-cases.json
```

- [ ] **Step 2: Commit**

Run:

```powershell
git commit -m "Harden memory capture style correction policy"
```

- [ ] **Step 3: Restart backend if needed**

If a backend process on `8081` predates the commit, restart it so runtime uses the new policy.

- [ ] **Step 4: Manual frontend verification**

After logging in, send:

```text
现在不要这么俏皮，要严肃深刻
```

Expected:
- AI replies in the requested serious style.
- AI 记忆面板 shows a new active memory with content like `希望交互风格严肃深刻，避免俏皮`.
- 事件记录 shows `CREATED` or `SUPERSEDED + CREATED` with a non-empty `traceId`.

---

## Self-Review

- Spec coverage: Covers stable style correction, false-positive protection, candidate normalization, governance event compatibility, and verification.
- Placeholder scan: No TODO/TBD placeholders remain.
- Type consistency: Uses existing `CaptureRequest`, `CaptureDecision`, `MemoryCandidate`, `DecisionType`, and existing test commands.
