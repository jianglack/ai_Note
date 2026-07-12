# Memory Answer Quality Evaluation Design

## Goal

Task 7 verifies that recalled long-term memory improves the final answer without overriding the user's current request, selected notes, retrieved note facts, or memory deletion controls.

This is an answer-quality evaluation. Capture-policy accuracy and retrieval ranking are inputs, but they are not substitutes for evaluating the answer returned by the configured production model.

## Review Findings

The existing system already tests memory capture, retrieval, context assembly, and selected-note isolation. It does not provide a paired, model-backed comparison of answers with and without memory.

The review also found a production-path gap: direct streaming skipped context assembly whenever no note was selected. Normal read-only questions could therefore bypass long-term memory even when retrieval was enabled.

## Evaluation Contract

Every case is evaluated as a pair with the same model configuration and user query:

- `CONTROL`: no active long-term memory is supplied.
- `TREATMENT`: the case-specific active memory and reference context are supplied.

The evaluator records both answers and an independent judgement. It never reads or mutates a real user's memory table.

Scenarios:

1. `PREFERENCE_ADHERENCE`: active language, format, or interaction preferences should improve adherence.
2. `MEMORY_BENEFIT`: stable user or project context should make the answer more useful and specific.
3. `STALE_MEMORY_ISOLATION`: stale or superseded context must not override the current request.
4. `SELECTED_NOTE_PRIORITY`: a question about the selected note must be answered from that note, not unrelated memory or global RAG.
5. `RAG_MEMORY_CONFLICT`: retrieved note facts are the source of truth for note-content questions; profile memory may shape presentation but not replace facts.
6. `DELETED_DISABLED_ISOLATION`: deleted or disabled memory must not appear in the supplied context or final answer.

## Scoring Model

Two complementary layers are required:

- Deterministic contracts check explicit expected and forbidden evidence, language constraints, and leakage markers.
- A model judge compares the paired answers against a versioned rubric and returns structured JSON scores for correctness, relevance, memory use, policy compliance, and treatment preference.

Deterministic hard boundaries cannot be overruled by the judge. Semantic quality thresholds can be calibrated from reviewed failures.

Primary metrics:

- availability rate;
- deterministic contract pass rate;
- judge pass rate;
- treatment win rate;
- harmful regression rate;
- missing-memory honesty rate;
- preference adherence rate;
- memory benefit rate;
- stale-memory isolation rate;
- selected-note priority rate;
- RAG/memory conflict resolution rate;
- deleted/disabled isolation rate;
- generation and judge p95 latency.

## Release Gate

Production defaults:

- at least 120 evaluated cases;
- availability at least 99.5%;
- deterministic and judge pass rates at least 95%;
- treatment win rate at least 80% for benefit-bearing scenarios;
- harmful regression rate at most 1%;
- missing-memory honesty rate must be 100%;
- selected-note, RAG conflict, stale-memory, and deleted/disabled isolation rates must be 100%;
- no timeout or unclassified error may be hidden from the report.

The gate is intentionally asymmetric: source-priority and deletion failures are hard safety failures, while stylistic quality is a calibratable quality metric.

## Dataset And Provenance

The formal dataset combines:

- reviewed, anonymized stable-preference statements from the existing external-real-human replay library;
- reviewed application-specific adversarial cases for selected notes, misleading RAG, stale corrections, and deletion/disable isolation;
- deterministic perturbations for Chinese, English, ambiguous wording, and complex project context.

Each case stores source type, source reference, scenario tags, review status, and dataset version. Generated perturbations are labelled as such and are never represented as direct human transcripts.

## Formal Run Controls

Live calls are opt-in and budget gated. Preflight blocks the run when the API key, model metadata, minimum case count, token cap, or cost cap is missing or exceeded.

The batch runner persists one JSONL progress record per case, supports resume without repeating completed cases, retries transient unavailable results with bounded backoff, and writes a final JSON report containing gate failures and failure evidence. Reports never contain credentials.

## Production Context Priority

All answer paths share this hierarchy:

1. current user request;
2. selected note for explicitly selected-note questions;
3. RAG snippets for facts contained in retrieved notes;
4. active long-term memory for user preferences and continuity.

Long-term memory may shape language, format, and continuity. It must not override current instructions or source facts. Deleted, disabled, superseded, retracted, or expired memory is excluded before prompt construction.
