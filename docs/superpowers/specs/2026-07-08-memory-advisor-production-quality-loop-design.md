# Memory Advisor Production Quality Loop Design

## Goal

Turn the memory advisor from a controllable experimental helper into an offline production-readiness loop. The loop must batch-evaluate the advisor on the active 4000-case replay dataset, compare it against the rule policy baseline, enforce explicit release gates, and preserve enough metadata to reproduce a result.

## Scope

This phase does not enable the LLM advisor in production traffic. It adds the evaluation and release gate that must pass before an advisor prompt/model can be considered production-ready.

## Architecture

Keep the existing `MemoryAdvisorReplayEvaluationService` as the low-level evaluator. Add a production-readiness layer above it:

- `MemoryAdvisorProductionQualityService`
  - Runs advisor replay evaluation.
  - Runs rule-policy replay baseline through `MemoryReplayEvaluationService`.
  - Measures advisor latency around each `MemorySignalAdvisor.advise` call.
  - Calculates release metrics and gate failures.
  - Builds a failure report that can be stored or copied into a review queue.
- `LlmMemorySignalAdvisor`
  - Exposes a stable prompt version and includes it in the prompt text so evaluation reports can identify the prompt being tested.

## Inputs

The service accepts:

- replay cases, normally `MemoryReplayDatasetLoader.loadActiveDataset().cases()`;
- a `MemorySignalAdvisor` candidate;
- run metadata: run ID, advisor name, model name, prompt version, dataset version;
- thresholds, normally production defaults.

## Metrics

The production report must include:

- total cases;
- advisor availability rate;
- advisor unavailable rate;
- capture decision accuracy;
- false positive rate;
- false negative rate;
- memory type accuracy;
- sensitive false-allow rate;
- p95 latency in milliseconds;
- rule baseline decision accuracy;
- advisor decision accuracy delta versus baseline.

## Quality Gates

Production defaults:

- at least 4000 replay cases;
- advisor availability rate at least `0.995`;
- capture decision accuracy at least `0.995`;
- false positive rate exactly `0.0`;
- false negative rate no more than `0.02`;
- memory type accuracy at least `0.98`;
- sensitive false-allow rate exactly `0.0`;
- p95 latency no more than `1500ms`;
- advisor decision accuracy cannot be worse than rule baseline by more than `0.005`;
- run metadata must include nonblank dataset, model, and prompt versions.

These defaults are intentionally strict. A lower-quality advisor can still be evaluated, but the readiness report must fail the release gate and list concrete reasons.

## Failure Report

The readiness report contains a failure report with:

- run ID;
- dataset version;
- model name;
- prompt version;
- total failure count;
- capped failure samples with case ID, mismatch messages, expected/actual capture decision, expected/actual memory type, and whether the case was a sensitive boundary.

The failure report is the bridge into the next human-review loop. It avoids raw model output dumps and stores only replay IDs plus sanitized mismatch details.

## Testing

Tests must cover:

- oracle advisor passes production gates on the active replay dataset;
- degraded advisor fails with gate failures and failure samples;
- sensitive false-allow cases are detected separately from generic false positives;
- prompt version is exposed and included in the LLM advisor prompt.
