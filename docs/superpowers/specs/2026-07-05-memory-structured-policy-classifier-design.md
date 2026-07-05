# Memory Structured Policy Classifier Design

## Goal

Move memory capture from a flat set of string checks to an auditable structured policy classifier, while keeping the existing governed write boundary intact.

This design does not replace `MemoryOrchestrator`, `MemoryCapturePolicy`, `MemoryCandidateExtractor`, `MemoryWriteService`, or the memory event ledger. It makes the capture decision layer more explicit, testable, and extensible.

## Current Problem

`MemoryCapturePolicy.evaluate(...)` currently owns normalization, signal detection, deny rules, allow rules, and priority ordering in one class. This works for the first L3 slice, but it has three weaknesses:

- Adding a new memory scenario means adding another condition directly inside `evaluate(...)`.
- Tests can verify final allow/deny decisions, but cannot inspect which signals were detected.
- A stable preference like "以后回答请严肃一些，少开玩笑" is conceptually clear, but current logic is still too dependent on exact contrastive wording.

The production risk is not only false negatives. If future rules are added casually, false positives can increase and RAG/note content may leak into user-profile memory.

## Design

Add a new backend service:

`backend/src/main/java/com/ainote/app/service/MemorySignalClassifier.java`

It classifies a capture request into structured signals before any final decision is made.

Primary output:

```java
record SignalClassification(
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
        List<String> matchedSignals)
```

`MemoryCapturePolicy` remains the only public decision boundary. It uses this classifier and applies a fixed priority order:

1. Missing user or blank message: deny transient.
2. Sensitive content: deny sensitive.
3. Forget request: deny as forget request.
4. Reference-only selected note or RAG context: deny tool/reference result.
5. Transient operation: deny transient.
6. One-off scope: deny transient.
7. Explicit remember: allow explicit.
8. Correction or interaction-style signal: allow implicit low confidence.
9. Preference or project-context signal: allow implicit low confidence.
10. Otherwise: deny transient.

This preserves the existing "deny before allow" governance rule.

## Capture Semantics

The first implementation slice should improve recall only for stable user-controlled preferences:

- Allow: "以后回答请严肃一些，少开玩笑。"
- Allow: "以后请默认用严肃、专业的语气回答。"
- Allow: "项目上下文：这个项目是一个 AI 笔记系统。"
- Deny: "这次回答请严肃一些。"
- Deny: "这篇笔记说我喜欢红色，请总结。"
- Deny: RAG output marked as `reference_only`.

Project-context capture is intentionally narrow. It only triggers when the user explicitly labels the sentence as project context, such as `项目上下文：...`, `项目背景：...`, or English `project context: ...`.

## Candidate Extraction

`MemoryCandidateExtractor` should continue to produce one candidate for allowed turns.

Small normalization additions are allowed:

- Stable style instruction can normalize to `希望交互风格严肃一些，少开玩笑`.
- Explicit project context can normalize to the content after the label and use:
  - `memoryType = "project_context"`
  - `category = "project_context"`
  - `scope = "project"`

Existing style-correction behavior remains compatible. The current event ledger and memory panel already support `style` and `project_context` filters, so no frontend change is required in this slice.

## Feature Flags And Rollback

No new database migration is needed.

The rollback path is the existing capture flag:

- `app.memory.capture.enabled=false` disables governed capture.
- `app.memory.capture.mode=legacy` can route away from the policy path if needed.

The code change is also revertible with a normal `git revert`.

## Testing

Add focused tests before implementation:

- `MemorySignalClassifierTest` verifies structured signals directly.
- `MemoryCapturePolicyTest` verifies final deny/allow decisions still follow priority order.
- `MemoryCandidateExtractorTest` verifies normalization for stable style and project context.
- `MemorySystemEvaluationTest` ensures the eval dataset contains these new cases.

Run focused tests, memory regression tests, and full backend tests before committing implementation.

## Non-Goals

- No LLM classifier in this slice.
- No frontend redesign.
- No schema changes.
- No automatic conversion of RAG or selected-note content into user memory.
- No broad extraction of arbitrary project facts unless the user explicitly labels them as project context.

## Self-Review

- Completion scan: no unfinished markers remain.
- Scope check: one backend policy-boundary hardening slice.
- Consistency check: `MemoryCapturePolicy` remains the decision owner; classifier only exposes signals.
- Risk check: deny rules stay higher priority than allow rules.
