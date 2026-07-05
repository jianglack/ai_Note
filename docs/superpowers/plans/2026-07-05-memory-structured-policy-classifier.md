# Memory Structured Policy Classifier Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a structured memory signal classifier so long-term memory capture decisions are auditable, extensible, and less dependent on one flat list of keyword checks.

**Architecture:** Keep `MemoryCapturePolicy` as the public decision boundary. Add `MemorySignalClassifier` to classify request signals, then let `MemoryCapturePolicy` apply fixed deny-before-allow priority. Keep `MemoryCandidateExtractor`, `MemoryWriteService`, event ledger, feature flags, and retrieval path intact.

**Tech Stack:** Spring Boot, Java records, JUnit 5, AssertJ, existing memory governance services and eval dataset.

---

## File Structure

- Create: `backend/src/main/java/com/ainote/app/service/MemorySignalClassifier.java`
  - Responsibility: normalize a capture request and expose structured policy signals plus matched signal names.
- Create: `backend/src/test/java/com/ainote/app/service/MemorySignalClassifierTest.java`
  - Responsibility: direct tests for signal detection and deny-priority inputs.
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryCapturePolicy.java`
  - Responsibility: consume classifier output and keep final allow/deny priority centralized.
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryCandidateExtractor.java`
  - Responsibility: normalize stable style preferences and explicit project context into durable candidate content.
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryCapturePolicyTest.java`
  - Responsibility: final policy behavior tests.
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryCandidateExtractorTest.java`
  - Responsibility: candidate type, scope, and content normalization tests.
- Modify: `backend/src/test/java/com/ainote/app/service/MemorySystemEvaluationTest.java`
  - Responsibility: require new governance eval cases.
- Modify: `backend/src/test/resources/memory/eval-cases.json`
  - Responsibility: add positive and negative policy scenarios.

---

### Task 1: Signal Classifier Tests

**Files:**
- Create: `backend/src/test/java/com/ainote/app/service/MemorySignalClassifierTest.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryCapturePolicyTest.java`
- Modify: `backend/src/test/resources/memory/eval-cases.json`
- Modify: `backend/src/test/java/com/ainote/app/service/MemorySystemEvaluationTest.java`

- [ ] **Step 1: Add classifier test file**

Create `MemorySignalClassifierTest.java` with these tests:

```java
package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemorySignalClassifierTest {

    private final MemorySignalClassifier classifier = new MemorySignalClassifier();

    @Test
    void classifiesStableStylePreferenceWithoutRememberKeyword() {
        MemorySignalClassifier.SignalClassification signals = classifier.classify(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "以后回答请严肃一些，少开玩笑。",
                        "收到。"));

        assertThat(signals.interactionStyleSignal()).isTrue();
        assertThat(signals.preferenceSignal()).isTrue();
        assertThat(signals.oneOffScope()).isFalse();
        assertThat(signals.matchedSignals()).contains("stable_style_preference");
    }

    @Test
    void classifiesOneOffScopeBeforeStylePreference() {
        MemorySignalClassifier.SignalClassification signals = classifier.classify(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "这次回答请严肃一些。",
                        "收到。"));

        assertThat(signals.oneOffScope()).isTrue();
        assertThat(signals.interactionStyleSignal()).isTrue();
        assertThat(signals.matchedSignals()).contains("one_off_scope", "one_off_style_preference");
    }

    @Test
    void classifiesReferenceOnlyEvenWhenPreferenceWordsAppear() {
        MemorySignalClassifier.SignalClassification signals = classifier.classify(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "这篇笔记说我喜欢红色，请总结。",
                        "这篇笔记提到了颜色偏好。"));

        assertThat(signals.referenceOnly()).isTrue();
        assertThat(signals.preferenceSignal()).isTrue();
        assertThat(signals.matchedSignals()).contains("reference_only");
    }

    @Test
    void classifiesExplicitProjectContext() {
        MemorySignalClassifier.SignalClassification signals = classifier.classify(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "项目上下文：这个项目是一个 AI 笔记系统。",
                        "已记录。"));

        assertThat(signals.projectContextSignal()).isTrue();
        assertThat(signals.matchedSignals()).contains("project_context");
    }
}
```

- [ ] **Step 2: Add policy tests**

Add these tests to `MemoryCapturePolicyTest`:

```java
@Test
void stableStylePreferenceWithoutRememberKeywordShouldBeAllowed() {
    MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(
            new MemoryCapturePolicy.CaptureRequest(
                    "user-1",
                    "以后回答请严肃一些，少开玩笑。",
                    "收到。"));

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.type()).isEqualTo(MemoryCapturePolicy.DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE);
    assertThat(decision.reason()).contains("interaction_style");
}

@Test
void referenceOnlyStillWinsOverPreferenceSignal() {
    MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(
            new MemoryCapturePolicy.CaptureRequest(
                    "user-1",
                    "这篇笔记说我喜欢红色，请总结。",
                    "这篇笔记提到了颜色偏好。"));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.type()).isEqualTo(MemoryCapturePolicy.DecisionType.DENY_TOOL_RESULT);
}

@Test
void explicitProjectContextShouldBeAllowed() {
    MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(
            new MemoryCapturePolicy.CaptureRequest(
                    "user-1",
                    "项目上下文：这个项目是一个 AI 笔记系统。",
                    "已记录。"));

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.reason()).contains("project_context");
}
```

- [ ] **Step 3: Extend eval dataset**

Add these objects to `backend/src/test/resources/memory/eval-cases.json`:

```json
{
  "id": "should_capture_stable_style_preference",
  "userMessage": "以后回答请严肃一些，少开玩笑。",
  "assistantOutput": "收到。",
  "expectedCaptureAllowed": true,
  "expectedDecisionType": "ALLOW_IMPLICIT_LOW_CONFIDENCE",
  "expectedMemoryType": "style",
  "expectedCorrection": false
},
{
  "id": "should_capture_explicit_project_context",
  "userMessage": "项目上下文：这个项目是一个 AI 笔记系统。",
  "assistantOutput": "已记录。",
  "expectedCaptureAllowed": true,
  "expectedDecisionType": "ALLOW_IMPLICIT_LOW_CONFIDENCE",
  "expectedMemoryType": "project_context",
  "expectedCorrection": false
},
{
  "id": "should_not_capture_reference_note_even_with_preference_words",
  "userMessage": "这篇笔记说我喜欢红色，请总结。",
  "assistantOutput": "这篇笔记提到了颜色偏好。",
  "expectedCaptureAllowed": false,
  "expectedDecisionType": "DENY_TOOL_RESULT"
}
```

Also change `should_capture_interaction_style_correction.expectedMemoryType` from `preference` to `style`.

- [ ] **Step 4: Require new eval IDs**

Add these IDs to `MemorySystemEvaluationTest.evalDatasetCoversRequiredGovernanceScenarios()`:

```java
"should_capture_stable_style_preference",
"should_capture_explicit_project_context",
"should_not_capture_reference_note_even_with_preference_words"
```

- [ ] **Step 5: Run RED verification**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemorySignalClassifierTest,MemoryCapturePolicyTest,MemorySystemEvaluationTest" test
```

Expected before implementation: fail to compile because `MemorySignalClassifier` does not exist, or fail policy/eval assertions if the test file has already compiled.

---

### Task 2: Implement MemorySignalClassifier

**Files:**
- Create: `backend/src/main/java/com/ainote/app/service/MemorySignalClassifier.java`
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryCapturePolicy.java`

- [ ] **Step 1: Add classifier implementation**

Create `MemorySignalClassifier.java`:

```java
package com.ainote.app.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class MemorySignalClassifier {

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(api[_ -]?key|secret|password|token|sk-[a-z0-9_-]+|AKIA[0-9A-Z]{16})");
    private static final Pattern INTERACTION_STYLE_SIGNAL = Pattern.compile(
            "(回答|回复|语气|口吻|风格|格式|详细|简短|严肃|深刻|正式|专业|俏皮|轻松|幽默|活泼|开玩笑|玩笑|啰嗦|精简|简洁|"
                    + "answer|reply|tone|style|format|formal|serious|concise|detailed|professional)");
    private static final Pattern PROJECT_CONTEXT_LABEL = Pattern.compile(
            "^(项目上下文|项目背景|project context|project background)[:：].+",
            Pattern.CASE_INSENSITIVE);

    public SignalClassification classify(MemoryCapturePolicy.CaptureRequest request) {
        String userId = request == null ? "" : request.userId();
        String userMessage = normalize(request == null ? "" : request.userMessage());
        String aiResponse = normalize(request == null ? "" : request.aiResponse());
        String compact = compact(userMessage);
        List<String> matchedSignals = new ArrayList<>();

        boolean validUser = userId != null && !userId.isBlank();
        boolean blankMessage = userMessage.isBlank();
        boolean sensitive = containsSensitive(userMessage) || containsSensitive(aiResponse);
        boolean forgetRequest = isForgetRequest(compact);
        boolean referenceOnly = isReferenceOnlyTask(compact) || isRagReference(aiResponse);
        boolean transientOperation = isTransientOperation(compact);
        boolean oneOffScope = isOneOffInstruction(compact);
        boolean explicitRemember = isExplicitRemember(compact);
        boolean correctionSignal = looksLikePreferenceCorrection(compact)
                || looksLikeInteractionStyleCorrection(userMessage, compact);
        boolean interactionStyleSignal = looksLikeInteractionStyleCorrection(userMessage, compact)
                || looksLikeStableStylePreference(userMessage, compact);
        boolean projectContextSignal = isProjectContext(userMessage);
        boolean preferenceSignal = looksLikePreference(compact) || interactionStyleSignal;

        addSignal(matchedSignals, !validUser, "missing_user");
        addSignal(matchedSignals, blankMessage, "blank_message");
        addSignal(matchedSignals, sensitive, "sensitive_content");
        addSignal(matchedSignals, forgetRequest, "forget_request");
        addSignal(matchedSignals, referenceOnly, "reference_only");
        addSignal(matchedSignals, transientOperation, "transient_operation");
        addSignal(matchedSignals, oneOffScope, "one_off_scope");
        addSignal(matchedSignals, explicitRemember, "explicit_remember");
        addSignal(matchedSignals, looksLikePreferenceCorrection(compact), "preference_correction");
        addSignal(matchedSignals, looksLikeInteractionStyleCorrection(userMessage, compact), "interaction_style_correction");
        addSignal(matchedSignals, looksLikeStableStylePreference(userMessage, compact), "stable_style_preference");
        addSignal(matchedSignals, projectContextSignal, "project_context");
        addSignal(matchedSignals, preferenceSignal && !interactionStyleSignal, "preference_signal");

        return new SignalClassification(
                validUser,
                blankMessage,
                sensitive,
                forgetRequest,
                referenceOnly,
                transientOperation,
                oneOffScope,
                explicitRemember,
                preferenceSignal,
                correctionSignal,
                interactionStyleSignal,
                projectContextSignal,
                List.copyOf(matchedSignals));
    }

    private void addSignal(List<String> matchedSignals, boolean condition, String signal) {
        if (condition) {
            matchedSignals.add(signal);
        }
    }

    private boolean containsSensitive(String text) {
        return SENSITIVE_PATTERN.matcher(text).find();
    }

    private boolean isExplicitRemember(String compact) {
        return compact.contains("记住")
                || compact.contains("請記住")
                || compact.contains("remember")
                || compact.contains("keepinmind");
    }

    private boolean looksLikePreference(String compact) {
        return compact.contains("我希望")
                || compact.contains("我喜欢")
                || compact.contains("我偏好")
                || compact.contains("iprefer")
                || compact.contains("ilike")
                || compact.contains("prefer")
                || compact.contains("like")
                || compact.contains("iwantyouto");
    }

    private boolean looksLikePreferenceCorrection(String compact) {
        return compact.contains("以后不要")
                || compact.contains("不再")
                || compact.contains("改为")
                || compact.contains("nolonger")
                || compact.contains("instead")
                || compact.contains("rather");
    }

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
                || compact.contains("more")
                || compact.contains("instead")
                || hasStandaloneChineseWantAfterNegativeDirective(compact);
        return hasNegativeDirective
                && hasPositiveReplacement
                && INTERACTION_STYLE_SIGNAL.matcher(userMessage).find();
    }

    private boolean looksLikeStableStylePreference(String userMessage, String compact) {
        boolean stableScope = compact.contains("以后")
                || compact.contains("今后")
                || compact.contains("之后")
                || compact.contains("接下来")
                || compact.contains("默认")
                || compact.contains("一直")
                || compact.contains("fromnowon")
                || compact.contains("bydefault")
                || compact.contains("always");
        boolean styleIntent = INTERACTION_STYLE_SIGNAL.matcher(userMessage).find();
        boolean directive = compact.contains("请")
                || compact.contains("希望")
                || compact.contains("要")
                || compact.contains("用")
                || compact.contains("回答")
                || compact.contains("回复")
                || compact.contains("answer")
                || compact.contains("reply");
        return (stableScope || compact.contains("以后请"))
                && styleIntent
                && directive;
    }

    private boolean hasStandaloneChineseWantAfterNegativeDirective(String compact) {
        int negativeIndex = firstNegativeDirectiveIndex(compact);
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

    private int firstNegativeDirectiveIndex(String compact) {
        int best = -1;
        for (String token : List.of("不要", "别", "少一点", "少点")) {
            int index = compact.indexOf(token);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private boolean isForgetRequest(String compact) {
        return compact.contains("忘记")
                || compact.contains("不要记住")
                || compact.contains("别记住")
                || compact.contains("forgetthis")
                || compact.contains("forgetthat");
    }

    private boolean isTransientOperation(String compact) {
        if (compact.equals("确认") || compact.equals("確定") || compact.equals("取消")
                || compact.equals("ok") || compact.equals("yes") || compact.equals("no")
                || compact.equals("confirm") || compact.equals("cancel")) {
            return true;
        }
        return compact.contains("删除全部笔记")
                || compact.contains("删除所有笔记")
                || compact.contains("清空笔记")
                || compact.contains("删掉全部笔记")
                || compact.contains("deleteallnotes")
                || compact.contains("deleteeverynote")
                || compact.contains("deletenote")
                || compact.contains("confirmdelete")
                || compact.contains("canceldelete");
    }

    private boolean isReferenceOnlyTask(String compact) {
        return compact.contains("总结这篇笔记")
                || compact.contains("总结当前笔记")
                || compact.contains("总结选中笔记")
                || compact.contains("这篇笔记")
                || compact.contains("当前笔记")
                || compact.contains("selectednote")
                || compact.contains("currentnote")
                || compact.contains("thisnote")
                || compact.contains("whatdoesthisnotesay")
                || compact.contains("summarizethisnote");
    }

    private boolean isOneOffInstruction(String compact) {
        return compact.contains("这次")
                || compact.contains("这一次")
                || compact.contains("本次")
                || compact.contains("本轮")
                || compact.contains("当前这次")
                || compact.contains("thisonce")
                || compact.contains("thisreply")
                || compact.contains("thistime")
                || compact.contains("forthisreply");
    }

    private boolean isProjectContext(String userMessage) {
        return PROJECT_CONTEXT_LABEL.matcher(userMessage).find();
    }

    private boolean isRagReference(String text) {
        return text.contains("<rag_context")
                || text.contains("reference_only")
                || text.contains("operation_target=\"false\"");
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private String compact(String text) {
        return text.replaceAll("\\s+", "");
    }

    public record SignalClassification(
            boolean validUser,
            boolean blankMessage,
            boolean sensitive,
            boolean forgetRequest,
            boolean referenceOnly,
            boolean transientOperation,
            boolean oneOffScope,
            boolean explicitRemember,
            boolean preferenceSignal,
            boolean correctionSignal,
            boolean interactionStyleSignal,
            boolean projectContextSignal,
            List<String> matchedSignals) {
    }
}
```

- [ ] **Step 2: Refactor policy to consume classifier**

Replace `MemoryCapturePolicy` internal helper-driven `evaluate(...)` logic with classifier output:

```java
private final MemorySignalClassifier signalClassifier;

@Autowired
public MemoryCapturePolicy(MemorySignalClassifier signalClassifier) {
    this.signalClassifier = signalClassifier;
}

MemoryCapturePolicy() {
    this(new MemorySignalClassifier());
}
```

Then in `evaluate(...)`:

```java
MemorySignalClassifier.SignalClassification signals = signalClassifier.classify(request);

if (!signals.validUser() || signals.blankMessage()) {
    return CaptureDecision.deny(DecisionType.DENY_TRANSIENT, "blank_or_missing_user");
}
if (signals.sensitive()) {
    return CaptureDecision.deny(DecisionType.DENY_SENSITIVE, "sensitive_content");
}
if (signals.forgetRequest()) {
    return CaptureDecision.deny(DecisionType.FORGET_REQUEST, "forget_request");
}
if (signals.referenceOnly()) {
    return CaptureDecision.deny(DecisionType.DENY_TOOL_RESULT, "reference_context_not_profile");
}
if (signals.transientOperation()) {
    return CaptureDecision.deny(DecisionType.DENY_TRANSIENT, "operation_or_confirmation");
}
if (signals.oneOffScope()) {
    return CaptureDecision.deny(DecisionType.DENY_TRANSIENT, "one_off_instruction");
}
if (signals.explicitRemember()) {
    return CaptureDecision.allow(DecisionType.ALLOW_EXPLICIT, "explicit_memory", 0.95);
}
if (signals.correctionSignal()) {
    String reason = signals.interactionStyleSignal()
            ? "correction_interaction_style"
            : "correction_preference";
    return CaptureDecision.allow(DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE, reason, 0.85);
}
if (signals.interactionStyleSignal()) {
    return CaptureDecision.allow(DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE, "implicit_interaction_style", 0.75);
}
if (signals.projectContextSignal()) {
    return CaptureDecision.allow(DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE, "project_context", 0.8);
}
if (signals.preferenceSignal()) {
    return CaptureDecision.allow(DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE, "implicit_preference", 0.65);
}
return CaptureDecision.deny(DecisionType.DENY_TRANSIENT, "no_stable_user_memory_signal");
```

Remove the old private helper methods and imports that moved to `MemorySignalClassifier`.

- [ ] **Step 3: Run classifier and policy tests**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemorySignalClassifierTest,MemoryCapturePolicyTest" test
```

Expected: classifier and policy tests pass.

---

### Task 3: Candidate Normalization For Style And Project Context

**Files:**
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryCandidateExtractorTest.java`
- Modify: `backend/src/main/java/com/ainote/app/service/MemoryCandidateExtractor.java`

- [ ] **Step 1: Add extractor tests**

Add to `MemoryCandidateExtractorTest`:

```java
@Test
void extractsStableStylePreferenceCandidate() {
    MemoryCapturePolicy.CaptureRequest request = new MemoryCapturePolicy.CaptureRequest(
            "user-1",
            "以后回答请严肃一些，少开玩笑。",
            "收到。");
    MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request);

    List<MemoryCandidateExtractor.MemoryCandidate> candidates = extractor.extract(request, decision);

    assertThat(candidates).hasSize(1);
    assertThat(candidates.get(0).memoryType()).isEqualTo("style");
    assertThat(candidates.get(0).category()).isEqualTo("preference");
    assertThat(candidates.get(0).scope()).isEqualTo("user");
    assertThat(candidates.get(0).content()).isEqualTo("希望交互风格严肃一些，少开玩笑");
    assertThat(candidates.get(0).correction()).isFalse();
}

@Test
void extractsProjectContextCandidate() {
    MemoryCapturePolicy.CaptureRequest request = new MemoryCapturePolicy.CaptureRequest(
            "user-1",
            "项目上下文：这个项目是一个 AI 笔记系统。",
            "已记录。");
    MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request);

    List<MemoryCandidateExtractor.MemoryCandidate> candidates = extractor.extract(request, decision);

    assertThat(candidates).hasSize(1);
    assertThat(candidates.get(0).memoryType()).isEqualTo("project_context");
    assertThat(candidates.get(0).category()).isEqualTo("project_context");
    assertThat(candidates.get(0).scope()).isEqualTo("project");
    assertThat(candidates.get(0).content()).isEqualTo("这个项目是一个 AI 笔记系统。");
}
```

Also extend `extractsImplicitStyleCorrectionCandidate`:

```java
assertThat(candidates.get(0).memoryType()).isEqualTo("style");
```

- [ ] **Step 2: Run extractor RED verification**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryCandidateExtractorTest" test
```

Expected before implementation: fail because style/project normalization is not implemented.

- [ ] **Step 3: Implement extractor normalization**

In `MemoryCandidateExtractor.java`, add:

```java
private static final Pattern CHINESE_STABLE_STYLE = Pattern.compile(
        "^(?:以后|今后|之后|接下来)(?:请)?(?:默认)?(?:回答|回复)?(?:请)?(?<style>.+)$");
private static final Pattern PROJECT_CONTEXT_LABEL = Pattern.compile(
        "^(?:项目上下文|项目背景|project context|project background)[:：]\\s*(?<content>.+)$",
        Pattern.CASE_INSENSITIVE);
```

At the start of `normalizeContent(...)`, before contrastive style:

```java
String projectContext = normalizeProjectContext(userMessage);
if (!projectContext.isBlank()) {
    return projectContext;
}
String stableStyle = normalizeStableStyle(userMessage);
if (!stableStyle.isBlank()) {
    return stableStyle;
}
```

Add helpers:

```java
private String normalizeProjectContext(String userMessage) {
    Matcher matcher = PROJECT_CONTEXT_LABEL.matcher(userMessage == null ? "" : userMessage.trim());
    if (!matcher.find()) {
        return "";
    }
    return matcher.group("content").trim();
}

private String normalizeStableStyle(String userMessage) {
    Matcher matcher = CHINESE_STABLE_STYLE.matcher(userMessage == null ? "" : userMessage.trim());
    if (!matcher.find()) {
        return "";
    }
    String style = cleanupStyleFragment(matcher.group("style"))
            .replaceFirst("^请", "")
            .replaceFirst("^用", "")
            .trim();
    if (style.isBlank()) {
        return "";
    }
    return "希望交互风格" + stripTrailingSentencePunctuation(style);
}

private String stripTrailingSentencePunctuation(String value) {
    return value == null ? "" : value.replaceFirst("[。.!！]+$", "").trim();
}

private boolean isProjectContext(String userMessage) {
    return PROJECT_CONTEXT_LABEL.matcher(userMessage == null ? "" : userMessage.trim()).find();
}

private boolean isStyleMemory(String userMessage, String content) {
    String normalized = (content == null ? "" : content) + " " + (userMessage == null ? "" : userMessage);
    return normalized.contains("交互风格")
            || normalized.contains("语气")
            || normalized.contains("口吻")
            || normalized.contains("严肃")
            || normalized.contains("专业")
            || normalized.contains("俏皮")
            || normalized.contains("开玩笑");
}
```

Change candidate construction:

```java
String memoryType = inferMemoryType(userMessage, content);
String category = "style".equals(memoryType) ? "preference" : memoryType;
double confidence = Math.max(decision.baseConfidence(), correction ? 0.9 : 0.0);
String scope = "project_context".equals(memoryType) ? "project" : "user";
return List.of(new MemoryCandidate(
        memoryType,
        category,
        content,
        confidence,
        scope,
        userMessage,
        correction
));
```

Add:

```java
private String inferMemoryType(String userMessage, String content) {
    if (isProjectContext(userMessage)) {
        return "project_context";
    }
    if (isStyleMemory(userMessage, content)) {
        return "style";
    }
    return inferCategory(content);
}
```

- [ ] **Step 4: Run extractor tests**

Run:

```powershell
mvn "-Dtest=MemoryCandidateExtractorTest" test
```

Expected: pass.

---

### Task 4: Governance Regression

**Files:**
- No production files expected.

- [ ] **Step 1: Run focused classifier/policy/eval tests**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemorySignalClassifierTest,MemoryCapturePolicyTest,MemoryCandidateExtractorTest,MemorySystemEvaluationTest" test
```

Expected: all selected tests pass.

- [ ] **Step 2: Run memory regression suite**

Run:

```powershell
mvn "-Dtest=MemoryCapturePolicyTest,MemorySignalClassifierTest,MemoryCandidateExtractorTest,MemorySystemEvaluationTest,MemoryWriteServiceTest,MemoryOrchestratorTest,MemoryExtractionServiceTest,MemoryRetrievalServiceTest,ContextAssemblerTest" test
```

Expected: all selected memory tests pass.

- [ ] **Step 3: Run full backend test suite**

Run:

```powershell
mvn test
```

Expected: build success. Do not bypass any failing tests.

- [ ] **Step 4: Check whitespace**

Run:

```powershell
cd D:\ainotetest
git diff --check
```

Expected: no whitespace errors. CRLF warnings are acceptable on this Windows workspace.

---

### Task 5: Runtime Check And Commit

**Files:**
- Commit only files listed in File Structure.

- [ ] **Step 1: Restart backend if it is running**

Run the existing local restart flow so runtime uses the new policy:

```powershell
cd D:\ainotetest
$listener = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
if ($listener) { Stop-Process -Id $listener -Force }
docker compose up -d postgres redis neo4j
$env:JWT_SECRET='codex_test_jwt_secret_20260705_abcdefghijklmnopqrstuvwxyz1234567890'
$env:POSTGRES_PASSWORD='ainote_dev_password'
$env:DEEPSEEK_API_KEY=[Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'User')
$env:DEEPSEEK_EMBEDDING_BASE_URL=[Environment]::GetEnvironmentVariable('DEEPSEEK_EMBEDDING_BASE_URL', 'User')
$process = Start-Process -FilePath 'mvn.cmd' -ArgumentList @('spring-boot:run') -WorkingDirectory 'D:\ainotetest\backend' -RedirectStandardOutput 'D:\ainotetest\backend\backend-runtime.log' -RedirectStandardError 'D:\ainotetest\backend\backend-runtime.err.log' -WindowStyle Hidden -PassThru
```

Then verify:

```powershell
Invoke-WebRequest -Uri http://127.0.0.1:8081/actuator/health -UseBasicParsing
```

Expected: HTTP 200 and `{"status":"UP"}`.

- [ ] **Step 2: Stage planned files**

Run:

```powershell
cd D:\ainotetest
git add backend/src/main/java/com/ainote/app/service/MemorySignalClassifier.java `
  backend/src/main/java/com/ainote/app/service/MemoryCapturePolicy.java `
  backend/src/main/java/com/ainote/app/service/MemoryCandidateExtractor.java `
  backend/src/test/java/com/ainote/app/service/MemorySignalClassifierTest.java `
  backend/src/test/java/com/ainote/app/service/MemoryCapturePolicyTest.java `
  backend/src/test/java/com/ainote/app/service/MemoryCandidateExtractorTest.java `
  backend/src/test/java/com/ainote/app/service/MemorySystemEvaluationTest.java `
  backend/src/test/resources/memory/eval-cases.json `
  docs/superpowers/plans/2026-07-05-memory-structured-policy-classifier.md
```

- [ ] **Step 3: Commit**

Run:

```powershell
git commit -m "Add structured memory signal classifier"
```

- [ ] **Step 4: Report verification**

Report:

- modified files
- behavior changes
- test commands and results
- runtime health state
- remaining risks

## Self-Review

- Spec coverage: covers structured classifier, policy priority, style preference recall, project context, and reference-only denial.
- Completion scan: no unfinished markers remain.
- Type consistency: `SignalClassification`, `MemorySignalClassifier`, `MemoryCapturePolicy`, and `MemoryCandidateExtractor.MemoryCandidate` signatures are consistent.
- Scope check: no schema, frontend, retrieval, or write-service changes in this slice.
