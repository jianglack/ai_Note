# Memory Replay Dataset Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand the memory replay dataset to an enterprise-oriented 200-case anonymous seed set with automated coverage, hygiene, and replay quality gates.

**Architecture:** Keep replay evaluation inside the existing backend test boundary. `MemoryReplayEvaluationServiceTest` owns dataset shape and policy replay gates, `MemoryAdvisorReplayEvaluationServiceTest` proves the same JSON can feed advisor evaluation, and `backend/src/test/resources/memory/replay-eval-cases.json` remains the single active replay dataset.

**Tech Stack:** Spring Boot backend, JUnit 5, AssertJ, Jackson, JSON test resources, PowerShell on Windows with explicit UTF-8 reads.

---

## Source Spec

Read before execution:

- `docs/superpowers/specs/2026-07-06-memory-replay-dataset-expansion-design.md`

The spec commit at planning time is:

```powershell
git show --name-only --format="%h %s" HEAD
```

Expected:

```text
082b479 docs: design memory replay dataset expansion
docs/superpowers/specs/2026-07-06-memory-replay-dataset-expansion-design.md
```

## File Responsibility Map

- `backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java`
  - Add enterprise dataset shape gates.
  - Keep strict policy replay quality metrics at 1.0.
  - Own helper methods for category parsing, unique ID checks, language checks, and required metadata checks.
- `backend/src/test/resources/memory/replay-eval-cases.json`
  - Active replay dataset.
  - Must be UTF-8.
  - Must contain at least 200 anonymous cases following `replay_<language>_<category>_<slug>`.
- `backend/src/test/java/com/ainote/app/service/MemoryAdvisorReplayEvaluationServiceTest.java`
  - Update dataset-size expectation from 25 to 200 after the dataset is expanded.
  - Keep oracle advisor behavior unchanged.
- `docs/superpowers/plans/2026-07-08-memory-replay-dataset-expansion.md`
  - This plan.

## Current Worktree State

Before execution, run:

```powershell
cd D:\ainotetest
git status --short --branch
```

Expected at planning time:

```text
## main...origin/main [ahead 1]
?? .superpowers/
?? docs/superpowers/plans/2026-07-03-agent-memory-system-optimization.md
?? docs/superpowers/plans/2026-07-03-ai-action-governance.md
?? docs/superpowers/plans/2026-07-03-chat-history-pagination.md
?? docs/superpowers/specs/2026-07-03-ai-action-governance-design.md
?? docs/superpowers/specs/2026-07-03-chat-history-pagination-design.md
```

Do not stage or modify the pre-existing untracked files listed above.

## Dataset Contract

The final active JSON dataset must satisfy:

| Category | Minimum Cases | Expected Side | Expected Decision/Reason/Signals |
| --- | ---: | --- | --- |
| `operation` | 15 | deny | `DENY_TRANSIENT`, `operation_or_confirmation`, `transient_operation` |
| `explicit_preference` | 15 | allow | `ALLOW_EXPLICIT`, `explicit_memory`, `explicit_remember`, `preference`, `false` |
| `implicit_preference` | 18 | allow | `ALLOW_IMPLICIT_LOW_CONFIDENCE`, `implicit_preference`, `preference_signal`, `preference`, `false` |
| `style` | 18 | allow | `ALLOW_IMPLICIT_LOW_CONFIDENCE`, `implicit_interaction_style`, `stable_style_preference`, `style`, `false` |
| `correction` | 14 | allow | `ALLOW_IMPLICIT_LOW_CONFIDENCE`, `correction_preference` or `correction_interaction_style`, matching correction signal, `preference` or `style`, `true` |
| `project_context` | 14 | allow | `ALLOW_IMPLICIT_LOW_CONFIDENCE`, `project_context`, `project_context`, `project_context`, `false` |
| `reference_only` | 15 | deny | `DENY_TOOL_RESULT`, `reference_context_not_profile`, `reference_only` |
| `rag_reference` | 15 | deny | `DENY_TOOL_RESULT`, `reference_context_not_profile`, `reference_only` |
| `one_off` | 14 | deny | `DENY_TRANSIENT`, `one_off_instruction`, `one_off_scope` |
| `assistant_feedback` | 10 | deny | `DENY_TRANSIENT`, `assistant_feedback`, `assistant_feedback` |
| `sensitive` | 10 | deny | `DENY_SENSITIVE`, `sensitive_content`, `sensitive_content` |
| `forget` | 10 | deny | `FORGET_REQUEST`, `forget_request`, `forget_request` |
| `ambiguous` | 12 | deny | `DENY_TRANSIENT`, `no_stable_user_memory_signal`, empty `expectedSignals` allowed |
| `multi_turn_correction` | 10 | allow | `ALLOW_IMPLICIT_LOW_CONFIDENCE`, correction reason, matching correction signal, `preference` or `style`, `true` |
| `complex_project_context` | 10 | allow | `ALLOW_IMPLICIT_LOW_CONFIDENCE`, `project_context`, `project_context`, `project_context`, `false` |

Minimum aggregate gates:

- total cases >= 200;
- Chinese cases >= 120;
- allow cases >= 70;
- deny cases >= 70;
- unique IDs;
- unique `userMessage` values;
- every ID follows `replay_<language>_<category>_<slug>`;
- every `cn` case contains at least one CJK character in `userMessage`;
- every case has nonblank `userMessage`, `assistantOutput`, `expectedDecisionType`, and `expectedPolicyReason`;
- every non-ambiguous case has at least one expected signal;
- allowed cases declare `expectedMemoryType` and `expectedCorrection`;
- deny cases omit `expectedMemoryType` and `expectedCorrection`.

## Supported Seed Patterns

Use only patterns the current policy and extractor already support unless a failing case is explicitly approved as a policy fix.

- Operation: `确认`, `取消`, `可以`, `好的`, `好`, `行`, `confirm`, `cancel`, `ok`, `yes`, `no`, `delete all notes`, `delete every note`, `confirm delete`, `cancel delete`, `删除全部笔记`, `清空笔记`.
- Explicit preference: include `记住`, `remember`, or `keep in mind`; keep content preference-oriented, such as reply language, export format, summary format, or task planning preference.
- Implicit preference: include `我希望`, `我喜欢`, `我偏好`, `I prefer`, or `I like`; avoid reply/style wording when expecting `implicit_preference`.
- Style: use Chinese stable-scope forms accepted by both classifier and extractor: `以后...回答...`, `今后...回复...`, `之后...回答...`, or `接下来...回复...`, with style words such as `严肃`, `专业`, `简洁`, `少开玩笑`.
- Correction: use `不再`, `改为`, `以后不要`, `no longer`, `instead`, or contrastive Chinese style such as `不要...要...`.
- Project context: start with `项目上下文：`, `项目背景：`, `project context:`, or `project background:`.
- Reference-only: include selected-note markers such as `这篇笔记`, `当前笔记`, `selected note`, `current note`, `this note`, or `summarize this note`.
- RAG reference: include `<rag_context role="reference_only">`, `reference_only`, or `operation_target="false"` in `assistantOutput`.
- One-off: include `这次`, `本次`, `本轮`, `this reply`, `this time`, or `for this reply`.
- Assistant feedback: mention the current answer or reply plus praise/thanks, such as `这个回答很好，谢谢` or `I like this answer, thanks`.
- Sensitive: use only fake examples containing `api key`, `secret`, `password`, `token`, `sk-test...`, or `AKIA0000000000000000`.
- Forget: include `忘记`, `不要记住`, `别记住`, `forget this`, or `forget that`.
- Ambiguous: use neutral utterances without stable memory signals, such as `这个问题我再想想`, `先放一边`, `I am still thinking about this`, or `Maybe later`.

## Task 0: Baseline And Encoding Check

**Files:**
- Read: `docs/superpowers/specs/2026-07-06-memory-replay-dataset-expansion-design.md`
- Read: `backend/src/test/resources/memory/replay-eval-cases.json`
- Read: `backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java`

- [ ] **Step 1: Check branch and dirty state**

Run:

```powershell
cd D:\ainotetest
git status --short --branch
```

Expected:

- Current branch is `main`.
- Only pre-existing untracked `.superpowers/` and `docs/superpowers/...2026-07-03...` files are listed before this plan is executed.

- [ ] **Step 2: Read replay JSON as UTF-8**

Run:

```powershell
cd D:\ainotetest
Get-Content -Raw -Encoding UTF8 backend/src/test/resources/memory/replay-eval-cases.json | Select-Object -First 1
```

Expected:

- Chinese text renders correctly, for example `删除全部笔记`.
- If Chinese text renders as mojibake, stop and inspect the file encoding before editing.

- [ ] **Step 3: Run current replay baseline**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryReplayEvaluationServiceTest,MemoryAdvisorReplayEvaluationServiceTest" test
```

Expected:

- Build success.
- Current tests pass before adding the new enterprise gate.

## Task 1: Add Enterprise Dataset Shape Gate

**Files:**
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java`

- [ ] **Step 1: Add imports for shape checks**

Add these imports below existing imports:

```java
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
```

- [ ] **Step 2: Add constants to `MemoryReplayEvaluationServiceTest`**

Add these fields below the `evaluator` field:

```java
    private static final int MIN_TOTAL_CASES = 200;
    private static final int MIN_CHINESE_CASES = 120;
    private static final int MIN_ALLOW_CASES = 70;
    private static final int MIN_DENY_CASES = 70;
    private static final Pattern CASE_ID_PATTERN =
            Pattern.compile("^replay_(cn|en)_[a-z]+(?:_[a-z]+)*_[a-z0-9]+(?:_[a-z0-9]+)*$");
    private static final Pattern CJK_PATTERN = Pattern.compile("\\p{IsHan}");
    private static final Set<String> VALID_DECISION_TYPES = EnumSet.allOf(MemoryCapturePolicy.DecisionType.class)
            .stream()
            .map(Enum::name)
            .collect(java.util.stream.Collectors.toSet());
    private static final Map<String, Integer> CATEGORY_MINIMUMS = categoryMinimums();
```

- [ ] **Step 3: Add the failing enterprise shape test**

Add this test method before `loadCases()`:

```java
    @Test
    void replayDatasetMeetsEnterpriseSeedShapeGate() throws Exception {
        List<MemoryReplayEvaluationService.MemoryReplayCase> cases = loadCases();
        Set<String> ids = new HashSet<>();
        Set<String> userMessages = new HashSet<>();
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();

        for (MemoryReplayEvaluationService.MemoryReplayCase replayCase : cases) {
            assertThat(replayCase.id()).as("id").isNotBlank();
            assertThat(replayCase.id()).as(replayCase.id()).matches(CASE_ID_PATTERN);
            assertThat(ids.add(replayCase.id())).as("duplicate id " + replayCase.id()).isTrue();
            assertThat(replayCase.userMessage()).as(replayCase.id()).isNotBlank();
            assertThat(userMessages.add(replayCase.userMessage()))
                    .as("duplicate userMessage " + replayCase.userMessage())
                    .isTrue();
            assertThat(replayCase.assistantOutput()).as(replayCase.id()).isNotBlank();
            assertThat(VALID_DECISION_TYPES).as(replayCase.id()).contains(replayCase.expectedDecisionType());
            assertThat(replayCase.expectedPolicyReason()).as(replayCase.id()).isNotBlank();

            String category = categoryOf(replayCase.id());
            assertThat(category).as(replayCase.id()).isNotBlank();
            categoryCounts.merge(category, 1, Integer::sum);

            if (replayCase.id().startsWith("replay_cn_")) {
                assertThat(CJK_PATTERN.matcher(replayCase.userMessage()).find())
                        .as(replayCase.id() + " should contain CJK text")
                        .isTrue();
            }

            if ("ambiguous".equals(category)
                    && "no_stable_user_memory_signal".equals(replayCase.expectedPolicyReason())) {
                assertThat(replayCase.expectedSignals()).as(replayCase.id()).isEmpty();
            } else {
                assertThat(replayCase.expectedSignals()).as(replayCase.id()).isNotEmpty();
                assertThat(replayCase.expectedSignals()).as(replayCase.id()).allSatisfy(signal ->
                        assertThat(signal).isNotBlank());
            }

            if (replayCase.expectedCaptureAllowed()) {
                assertThat(replayCase.expectedMemoryType()).as(replayCase.id()).isNotBlank();
                assertThat(replayCase.expectedCorrection()).as(replayCase.id()).isNotNull();
            } else {
                assertThat(replayCase.expectedMemoryType()).as(replayCase.id()).isBlank();
                assertThat(replayCase.expectedCorrection()).as(replayCase.id()).isNull();
            }
        }

        assertThat(cases).hasSizeGreaterThanOrEqualTo(MIN_TOTAL_CASES);
        assertThat(cases.stream().filter(MemoryReplayEvaluationServiceTest::isChineseCase).count())
                .isGreaterThanOrEqualTo(MIN_CHINESE_CASES);
        assertThat(cases.stream().filter(MemoryReplayEvaluationService.MemoryReplayCase::expectedCaptureAllowed).count())
                .isGreaterThanOrEqualTo(MIN_ALLOW_CASES);
        assertThat(cases.stream().filter(replayCase -> !replayCase.expectedCaptureAllowed()).count())
                .isGreaterThanOrEqualTo(MIN_DENY_CASES);

        CATEGORY_MINIMUMS.forEach((category, minimum) ->
                assertThat(categoryCounts.getOrDefault(category, 0))
                        .as(category)
                        .isGreaterThanOrEqualTo(minimum));
    }
```

- [ ] **Step 4: Add helper methods**

Add these methods below the test and above `loadCases()`:

```java
    private static Map<String, Integer> categoryMinimums() {
        Map<String, Integer> minimums = new LinkedHashMap<>();
        minimums.put("operation", 15);
        minimums.put("explicit_preference", 15);
        minimums.put("implicit_preference", 18);
        minimums.put("style", 18);
        minimums.put("correction", 14);
        minimums.put("project_context", 14);
        minimums.put("reference_only", 15);
        minimums.put("rag_reference", 15);
        minimums.put("one_off", 14);
        minimums.put("assistant_feedback", 10);
        minimums.put("sensitive", 10);
        minimums.put("forget", 10);
        minimums.put("ambiguous", 12);
        minimums.put("multi_turn_correction", 10);
        minimums.put("complex_project_context", 10);
        return minimums;
    }

    private static boolean isChineseCase(MemoryReplayEvaluationService.MemoryReplayCase replayCase) {
        return replayCase.id().startsWith("replay_cn_");
    }

    private static String categoryOf(String id) {
        String withoutLanguage = id == null ? "" : id.replaceFirst("^replay_(cn|en)_", "");
        for (String category : CATEGORY_MINIMUMS.keySet()) {
            if (withoutLanguage.startsWith(category + "_")) {
                return category;
            }
        }
        return "";
    }
```

- [ ] **Step 5: Run the test to verify RED**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryReplayEvaluationServiceTest#replayDatasetMeetsEnterpriseSeedShapeGate" test
```

Expected:

- Build fails.
- Failure mentions at least one current dataset issue, such as total size below 200 or old IDs like `replay_delete_all_notes` not matching `replay_<language>_<category>_<slug>`.

- [ ] **Step 6: Commit the RED gate**

Run:

```powershell
cd D:\ainotetest
git add backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java
git commit -m "test: add memory replay dataset shape gate"
```

Expected:

- Commit succeeds with only `MemoryReplayEvaluationServiceTest.java`.
- Tests are intentionally red at this commit.

## Task 2: Expand Replay Dataset To 200 Cases

**Files:**
- Modify: `backend/src/test/resources/memory/replay-eval-cases.json`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java`
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryAdvisorReplayEvaluationServiceTest.java`

- [ ] **Step 1: Replace old legacy IDs in the active JSON**

Rewrite every case ID to `replay_<language>_<category>_<slug>`. Preserve existing cases by renaming them where possible:

```text
replay_delete_all_notes -> replay_en_operation_delete_all_notes
replay_confirm -> replay_en_operation_confirm
replay_cancel -> replay_en_operation_cancel
replay_explicit_preference -> replay_en_explicit_preference_concise_answers
replay_implicit_preference -> replay_en_implicit_preference_dark_mode
replay_preference_correction -> replay_en_correction_chinese_instead_of_english
replay_project_context -> replay_en_project_context_ai_note_system
replay_selected_note_reference -> replay_en_reference_only_selected_note_red
replay_rag_reference_profile -> replay_en_rag_reference_profile_red
replay_one_off_reply_style -> replay_en_one_off_formal_concise_reply
replay_positive_feedback -> replay_en_assistant_feedback_like_answer
replay_sensitive_secret -> replay_en_sensitive_api_key
```

Chinese legacy cases should be renamed into matching `replay_cn_...` categories.

- [ ] **Step 2: Author additional anonymous seed cases**

Append cases until the dataset reaches exactly 200 cases using the Dataset Contract table. Use UTF-8 and avoid real identifiers.

For each case, use these field shapes:

Deny case:

```json
{
  "id": "replay_cn_operation_delete_all_notes",
  "userMessage": "删除全部笔记",
  "assistantOutput": "需要你确认后我才会删除。",
  "expectedCaptureAllowed": false,
  "expectedDecisionType": "DENY_TRANSIENT",
  "expectedPolicyReason": "operation_or_confirmation",
  "expectedSignals": ["transient_operation"]
}
```

Allow case:

```json
{
  "id": "replay_cn_explicit_preference_conclusion_first",
  "userMessage": "记住，我希望所有回答都先给结论。",
  "assistantOutput": "已记录。",
  "expectedCaptureAllowed": true,
  "expectedDecisionType": "ALLOW_EXPLICIT",
  "expectedMemoryType": "preference",
  "expectedCorrection": false,
  "expectedPolicyReason": "explicit_memory",
  "expectedSignals": ["explicit_remember"]
}
```

Ambiguous deny case:

```json
{
  "id": "replay_cn_ambiguous_thinking_about_topic",
  "userMessage": "这个问题我再想想。",
  "assistantOutput": "好的。",
  "expectedCaptureAllowed": false,
  "expectedDecisionType": "DENY_TRANSIENT",
  "expectedPolicyReason": "no_stable_user_memory_signal",
  "expectedSignals": []
}
```

- [ ] **Step 3: Use these supported category counts**

The final JSON must include at least these counts:

```text
operation: 15
explicit_preference: 15
implicit_preference: 18
style: 18
correction: 14
project_context: 14
reference_only: 15
rag_reference: 15
one_off: 14
assistant_feedback: 10
sensitive: 10
forget: 10
ambiguous: 12
multi_turn_correction: 10
complex_project_context: 10
```

Do not add unsupported examples just to increase volume. If a case does not match current policy signals, either make it an `ambiguous` case with `no_stable_user_memory_signal` or document it as future work outside the active JSON.

- [ ] **Step 4: Update representative ID assertions in `MemoryReplayEvaluationServiceTest`**

Replace the old `contains(...)` list inside `replayDatasetMeetsL3MemoryGovernanceGates` with representative canonical IDs:

```java
                .contains(
                        "replay_en_operation_delete_all_notes",
                        "replay_en_operation_confirm",
                        "replay_en_operation_cancel",
                        "replay_en_explicit_preference_concise_answers",
                        "replay_en_implicit_preference_dark_mode",
                        "replay_en_correction_chinese_instead_of_english",
                        "replay_en_project_context_ai_note_system",
                        "replay_en_reference_only_selected_note_red",
                        "replay_en_rag_reference_profile_red",
                        "replay_en_one_off_formal_concise_reply",
                        "replay_en_assistant_feedback_like_answer",
                        "replay_en_sensitive_api_key",
                        "replay_cn_operation_delete_all_notes",
                        "replay_cn_operation_confirm_can",
                        "replay_cn_operation_confirm_ok",
                        "replay_cn_explicit_preference_conclusion_first",
                        "replay_cn_style_serious_less_joking",
                        "replay_cn_correction_serious_instead_of_playful",
                        "replay_cn_project_context_internal_ai_note_system",
                        "replay_cn_reference_only_selected_note_red",
                        "replay_cn_rag_reference_profile_red",
                        "replay_cn_one_off_serious_reply",
                        "replay_cn_assistant_feedback_like_answer",
                        "replay_cn_sensitive_api_key",
                        "replay_cn_forget_dark_mode");
```

Change the old size assertions:

```java
        assertThat(cases).hasSizeGreaterThanOrEqualTo(25);
```

to:

```java
        assertThat(cases).hasSizeGreaterThanOrEqualTo(MIN_TOTAL_CASES);
```

Change:

```java
        assertThat(report.totalCases()).isGreaterThanOrEqualTo(12);
```

to:

```java
        assertThat(report.totalCases()).isGreaterThanOrEqualTo(MIN_TOTAL_CASES);
```

- [ ] **Step 5: Update advisor dataset-size assertion**

In `MemoryAdvisorReplayEvaluationServiceTest.replayDatasetCanBeUsedAsAdvisorEvalSet`, change:

```java
        assertThat(report.totalCases()).isGreaterThanOrEqualTo(25);
```

to:

```java
        assertThat(report.totalCases()).isGreaterThanOrEqualTo(200);
```

- [ ] **Step 6: Run focused shape test to verify GREEN**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryReplayEvaluationServiceTest#replayDatasetMeetsEnterpriseSeedShapeGate" test
```

Expected:

- Build success.
- The new enterprise shape gate passes.

- [ ] **Step 7: Run replay and advisor tests**

Run:

```powershell
mvn "-Dtest=MemoryReplayEvaluationServiceTest,MemoryAdvisorReplayEvaluationServiceTest" test
```

Expected:

- Build success.
- Replay metrics remain 1.0.
- Advisor oracle still reports 1.0 quality metrics.

- [ ] **Step 8: Commit expanded dataset**

Run:

```powershell
cd D:\ainotetest
git add backend/src/test/resources/memory/replay-eval-cases.json backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java backend/src/test/java/com/ainote/app/service/MemoryAdvisorReplayEvaluationServiceTest.java
git commit -m "test: expand memory replay dataset"
```

Expected:

- Commit succeeds.
- Only the active JSON and two replay test files are committed.

## Task 3: Guard Against Encoding And Overfitting Regressions

**Files:**
- Modify: `backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java`

- [ ] **Step 1: Add a UTF-8 readability assertion**

Add this assertion inside `replayDatasetMeetsEnterpriseSeedShapeGate` after loading cases:

```java
        assertThat(cases)
                .extracting(MemoryReplayEvaluationService.MemoryReplayCase::userMessage)
                .anySatisfy(message -> assertThat(message).contains("删除全部笔记"));
```

This catches accidental mojibake rewrites of the JSON file.

- [ ] **Step 2: Add a category spread assertion**

Add this assertion after category minimum checks:

```java
        assertThat(categoryCounts).hasSize(CATEGORY_MINIMUMS.size());
```

This ensures every required category appears in the active dataset.

- [ ] **Step 3: Run the shape test**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryReplayEvaluationServiceTest#replayDatasetMeetsEnterpriseSeedShapeGate" test
```

Expected:

- Build success.

- [ ] **Step 4: Commit regression guard refinements**

Run:

```powershell
cd D:\ainotetest
git add backend/src/test/java/com/ainote/app/service/MemoryReplayEvaluationServiceTest.java
git commit -m "test: guard memory replay dataset hygiene"
```

Expected:

- Commit succeeds with only `MemoryReplayEvaluationServiceTest.java`.

## Task 4: Focused Memory Evaluation Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused memory evaluation suite**

Run:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryReplayEvaluationServiceTest,MemoryAdvisorReplayEvaluationServiceTest,MemorySystemEvaluationTest,MemoryCapturePolicyTest,MemoryCandidateExtractorTest,MemorySignalClassifierTest" test
```

Expected:

- Build success.
- No test failures.

- [ ] **Step 2: Run broader memory regression suite**

Run:

```powershell
mvn "-Dtest=MemoryReplayEvaluationServiceTest,MemoryAdvisorReplayEvaluationServiceTest,MemorySystemEvaluationTest,MemoryCapturePolicyTest,MemoryCandidateExtractorTest,MemorySignalClassifierTest,MemoryControlServiceTest,MemoryControllerTest,MemoryRetrievalServiceTest,MemoryWriteServiceTest,MemoryOrchestratorTest,ContextAssemblerTest,ReliableChatMemoryStoreFlushTest" test
```

Expected:

- Build success.
- No test failures.

- [ ] **Step 3: Run full backend test suite if focused suite passes**

Run:

```powershell
mvn test
```

Expected:

- Build success.
- If unrelated tests fail, record exact failing class and method. Do not claim full backend green.

## Task 5: Final Diff Review And Report

**Files:**
- Read: all changed files.

- [ ] **Step 1: Check diff hygiene**

Run:

```powershell
cd D:\ainotetest
git diff --check
git status --short --branch
```

Expected:

- No whitespace errors.
- Branch is ahead by the implementation commits.
- Pre-existing untracked `.superpowers/` and 2026-07-03 docs remain untracked and unstaged.

- [ ] **Step 2: Inspect committed changes**

Run:

```powershell
git log --oneline --decorate -5
git show --stat --oneline HEAD
```

Expected:

- Recent commits include:
  - `test: add memory replay dataset shape gate`
  - `test: expand memory replay dataset`
  - `test: guard memory replay dataset hygiene`
- Diff stat shows only test files and `backend/src/test/resources/memory/replay-eval-cases.json`.

- [ ] **Step 3: Report verification evidence**

Report:

- total replay cases;
- Chinese case count;
- allow and deny counts;
- category counts;
- focused memory suite result;
- broader memory regression result;
- full backend suite result or exact unrelated failures.

## Future Fixtures Not In This Slice

Do not put these into the active JSON unless a later spec expands scope:

- real anonymized production conversations;
- real LLM advisor outputs;
- human review failures;
- answer-quality comparisons after retrieval;
- privacy/PII compliance fixtures requiring stronger PII detection;
- monitoring metrics fixtures.

## Self-Review

- Spec coverage: covers 200-case dataset, 120 Chinese cases, 70 allow/deny minimums, category counts, unique IDs, unique user messages, UTF-8, strict replay metrics, and advisor compatibility.
- Unfinished-marker scan: no task asks the implementer to invent unspecified behavior.
- Type consistency: uses existing `MemoryReplayEvaluationService.MemoryReplayCase`, `MemoryCapturePolicy.DecisionType`, JUnit 5, AssertJ, and current test class names.
- Scope check: no production code, frontend, database, real LLM, prompt, or human review workflow is included.
