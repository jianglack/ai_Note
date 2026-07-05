# Memory Advisory Classifier Design

## Goal

Improve memory capture recall for stable user preferences without weakening the governed memory boundary.

The current deterministic classifier is auditable but still misses semantically clear statements that do not contain the exact rule words. This slice adds a controlled advisory classifier that can add structured signals, while `MemoryCapturePolicy` remains the only final decision maker.

## Design

Add a new advisory boundary:

`MemorySignalAdvisor`

The existing flow becomes:

`MemorySignalClassifier -> optional MemorySignalAdvisor -> MemoryCapturePolicy -> MemoryCandidateExtractor -> MemoryWriteService`

The advisor is not a writer. It returns a small structured suggestion:

- confidence
- memory type hint
- matched advisory signals
- reason

The deterministic classifier still computes all hard deny signals first. The advisor is skipped when any hard deny signal is present:

- missing user
- blank message
- sensitive content
- forget request
- selected note or RAG reference
- transient operation
- one-off scope

If the advisor fails, returns malformed JSON, or returns confidence below threshold, the system falls back to deterministic signals and records an explanatory signal such as `advisor_failed` or `advisor_low_confidence`.

## Feature Flag

Add configuration under `app.memory.capture.advisor`:

- `enabled`: default `false`
- `min-confidence`: default `0.82`

When disabled, the system behaves exactly like the current rule-only classifier.

## Safety Rules

The advisor may only add allow-side signals:

- `advisor_preference_signal`
- `advisor_interaction_style_signal`
- `advisor_project_context_signal`

It may not remove deny signals. It may not mark RAG or selected-note content as profile memory. It may not override one-off instructions.

`MemoryCapturePolicy` continues to apply deny-before-allow priority.

## Prompt Shape

The LLM prompt must ask for strict JSON only:

```json
{
  "should_capture": true,
  "memory_type": "preference",
  "confidence": 0.86,
  "signals": ["advisor_preference_signal"],
  "reason": "stable user preference"
}
```

Allowed `memory_type` values are:

- `preference`
- `style`
- `project_context`
- `none`

## Non-Goals

- No automatic extraction from RAG or selected-note content.
- No database migration.
- No frontend changes in this slice.
- No direct writes from the LLM output.
- No broad user profiling from arbitrary text.

## Tests

The implementation must prove:

- Advisor is disabled by default.
- Advisor is not called for hard deny inputs.
- Advisor low-confidence suggestions are ignored.
- Advisor high-confidence stable preference suggestions can add allow-side policy signals.
- Advisor failure is observable through matched signals and does not allow capture.
- Existing memory governance eval cases still pass.
