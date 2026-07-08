# Memory Advisor Production Closed Loop V2 Design

## Goal

Complete task 2 by turning the LLM memory advisor from an evaluatable helper into an auditable production-quality loop. The loop must support real-model batch evaluation, threshold calibration, prompt version governance, advisor failure-rate monitoring, advisor-versus-rule A/B comparison, and an explicit pre-release quality gate.

## Enterprise Standard

This task follows the memory-system operating standard:

1. Design the closed loop before implementation.
2. Write a concrete implementation plan.
3. Review the design and plan before changing behavior.
4. Execute with tests first.
5. Re-audit results before claiming completion.

Passing unit tests alone is not completion. Completion requires a persisted release-readiness package that explains what model, prompt, dataset, thresholds, failures, and baseline comparison were used.

## Current State

The project already has these building blocks:

- `LlmMemorySignalAdvisor`
  - Default disabled in runtime configuration.
  - Exposes `PROMPT_VERSION`, currently `memory-advisor-v2`.
  - Parses strict JSON advisor responses.
- `MemoryAdvisorReplayEvaluationService`
  - Evaluates advisor decisions against replay cases.
  - Reports availability, decision accuracy, false-positive rate, false-negative rate, memory-type accuracy, p95 latency, failures, and per-case results.
- `MemoryAdvisorProductionQualityService`
  - Compares advisor output with rule-policy baseline.
  - Applies strict production thresholds.
  - Emits a readiness report and capped failure report.
- `MemoryAdvisorFormalEvaluationService`
  - Performs budget preflight.
  - Blocks unsafe live runs before API calls.
  - Persists JSON reports.
- `MemoryAdvisorFormalBatchEvaluationService`
  - Runs model-backed evaluation with progress JSONL.
  - Supports resume and retry of unavailable rows.
- `MemoryAdvisorFormalEvaluationIT`
  - Provides the opt-in live DeepSeek-compatible evaluation entry point.
- Active replay data now includes the strict 2000-case external real-human sample library from task 1.

The remaining gap is not absence of evaluators. The gap is that the system does not yet produce a complete production-quality decision package or enforce a repeatable release workflow for advisor changes.

## Scope

This task covers only advisor production-quality closure.

In scope:

- Real-model batch evaluation using `memory-advisor-v2`.
- Formal report validation that rejects stale prompt versions.
- Prompt version registry with prompt hash and compatibility checks.
- Threshold calibration report derived from evaluation results.
- Advisor failure-rate and retry observability metrics in the persisted report.
- Advisor-versus-rule A/B comparison with baseline deltas.
- Pre-release gate artifact that can be run locally or in CI.
- Tests proving stale reports, missing calibration, missing A/B comparison, or failed gates block release readiness.

Out of scope:

- Enabling the advisor for production traffic.
- Human review queue implementation. That is task 3.
- Frontend explainability. That is task 4.
- Broader privacy/compliance controls. That is task 5.
- Production dashboard UI. Task 2 produces metrics and machine-readable reports; task 6 turns them into dashboards.

## Architecture

Add a closed-loop layer above the existing formal and production quality services:

- `MemoryAdvisorPromptRegistry`
  - Owns prompt-version metadata.
  - Provides current prompt version, prompt hash, schema version, model-family compatibility, and status.
  - Rejects unknown or stale prompt versions in release-readiness validation.
- `MemoryAdvisorCalibrationService`
  - Consumes advisor evaluation results.
  - Computes candidate thresholds, current-threshold metrics, recommended threshold, and rejected alternatives.
  - Does not mutate runtime configuration automatically.
- `MemoryAdvisorAbComparisonService`
  - Compares advisor decisions against the rule baseline case by case.
  - Emits summary metrics and categorized deltas: advisor-only allow, rule-only allow, both allow, both deny, advisor regression, advisor improvement.
- `MemoryAdvisorReleaseGateService`
  - Consumes a formal batch report or readiness report.
  - Validates prompt metadata, dataset size, production gates, calibration presence, A/B comparison presence, failure-rate metrics, and report freshness.
  - Emits a release decision: `PASS` or `BLOCKED`, with concrete block reasons.
- Existing formal batch evaluation path
  - Remains the way to run real-model evaluation.
  - Gains a richer report shape that includes calibration, A/B comparison, advisor failure metrics, prompt metadata, and release gate decision.

This keeps evaluation, calibration, comparison, and release gating separate. Each part can be tested independently and audited from the persisted JSON report.

## Data Flow

1. Load active replay dataset.
2. Run preflight budget checks.
3. Run real advisor batch evaluation or reuse completed progress.
4. Build advisor readiness metrics through `MemoryAdvisorProductionQualityService`.
5. Load prompt metadata for `LlmMemorySignalAdvisor.PROMPT_VERSION`.
6. Build threshold calibration from per-case advisor results.
7. Build A/B comparison against `MemoryCapturePolicy` rule baseline.
8. Build advisor failure observability metrics.
9. Run release gate validation.
10. Persist one JSON release-readiness package.

The persisted report is the source of truth for task 2 review.

## Prompt Version Governance

Prompt metadata must include:

- `promptVersion`
- `schemaVersion`
- `promptHash`
- `allowedMemoryTypes`
- `allowedSignals`
- `compatibleModelFamilies`
- `status`: `candidate`, `approved`, `deprecated`

The current prompt version is `memory-advisor-v2`. A formal report whose prompt version does not match the current registry entry is stale and cannot pass the release gate.

Prompt version approval does not mean production enablement. It only means this prompt is eligible for formal evaluation and release-gate review.

## Threshold Calibration

Calibration must be reported, not silently applied.

The calibration report includes:

- evaluated case count;
- positive and negative case counts;
- current minimum advisor confidence;
- recommended minimum advisor confidence;
- capture decision accuracy at current and recommended thresholds;
- false-positive and false-negative rates at current and recommended thresholds;
- memory-type accuracy at current and recommended thresholds;
- sensitive false-allow rate at current and recommended thresholds;
- a table of candidate thresholds from `0.50` to `0.95` in `0.05` increments;
- rationale for the recommended threshold.

The recommended threshold must prefer zero sensitive false-allow, then zero or minimal false-positive rate, then lowest false-negative rate, then higher memory-type accuracy. If no candidate meets production thresholds, calibration must explicitly report that no deployable threshold exists.

## Advisor Failure-Rate Monitoring

The report must expose:

- total evaluated cases;
- available cases;
- unavailable cases;
- timeout cases;
- error cases;
- retry-attempt count;
- retried-case count;
- final unavailable rate;
- timeout rate;
- error rate;
- p50, p95, and max latency;
- top unavailable reasons.

These metrics are persisted for task 6 dashboard integration. Task 2 does not build the dashboard.

## Advisor Versus Rule A/B

The A/B comparison treats the existing rule policy as baseline.

The report includes:

- baseline decision accuracy;
- advisor decision accuracy;
- advisor delta versus baseline;
- baseline false-positive and false-negative rates;
- advisor false-positive and false-negative rates;
- case counts for both allow, both deny, advisor-only allow, and rule-only allow;
- categorized samples for advisor improvements and advisor regressions.

The release gate blocks if advisor decision accuracy regresses beyond the configured production threshold or if any sensitive case is advisor-only allow.

## Release Gate

The release gate returns:

- `status`: `PASS` or `BLOCKED`;
- `runId`;
- `datasetVersion`;
- `modelName`;
- `promptVersion`;
- `promptHash`;
- `evaluatedCases`;
- `qualityGatePassed`;
- `blockReasons`;
- `generatedAt`;
- links or paths to the batch report and progress file.

The release gate blocks when any of these are true:

- prompt version is missing, unknown, deprecated, or stale;
- prompt hash does not match the registry;
- evaluated cases are below the production minimum;
- formal readiness quality gate failed;
- calibration report is missing;
- calibration has no deployable threshold;
- A/B comparison is missing;
- advisor regresses beyond the baseline delta threshold;
- sensitive false-allow rate is above zero;
- availability is below threshold;
- timeout or error rate is above threshold;
- report is older than the configured freshness window.

The gate does not enable the advisor. It only decides whether this advisor prompt/model/dataset result is release-ready.

## Formal Evaluation Execution

The live run remains opt-in and budget-gated.

Expected v2 command shape:

```powershell
cd D:\ainotetest\backend
$env:MEMORY_ADVISOR_FORMAL_EVAL_ENABLED="true"
$env:DEEPSEEK_API_KEY="<configured locally>"
$env:MEMORY_ADVISOR_FORMAL_EVAL_RUN_ID="memory-advisor-v2-formal-eval-4000"
$env:MEMORY_ADVISOR_FORMAL_EVAL_MAX_CASES="4000"
$env:MEMORY_ADVISOR_FORMAL_EVAL_MIN_CASES="4000"
$env:MEMORY_ADVISOR_FORMAL_EVAL_MAX_COST_YUAN="20"
$env:MEMORY_ADVISOR_FORMAL_EVAL_RETRY_UNAVAILABLE_PROGRESS="true"
mvn "-Dtest=MemoryAdvisorFormalEvaluationIT" test
```

The report must record `promptVersion=memory-advisor-v2`. A report with `memory-advisor-v1` is retained as historical evidence but cannot satisfy task 2.

## Testing Strategy

Tests must cover:

- prompt registry returns metadata for `memory-advisor-v2`;
- unknown prompt version blocks release readiness;
- stale v1 formal report blocks release readiness;
- missing calibration blocks release readiness;
- missing A/B comparison blocks release readiness;
- a report with failed production quality gates blocks release readiness;
- oracle advisor with calibration and A/B data can pass the release gate;
- threshold calibration chooses a deployable threshold when one exists;
- threshold calibration reports no deployable threshold when sensitive false-allow cannot be eliminated;
- advisor failure metrics count unavailable, timeout, error, retried, and latency buckets correctly;
- persisted batch report includes prompt metadata, calibration, A/B comparison, failure metrics, and release gate decision.

Full backend tests must pass before this task can be called complete.

## Completion Criteria

Task 2 is complete only when all of these are true:

1. The v2 closed-loop design and implementation plan are committed.
2. The implementation is committed.
3. Local tests prove the production closed-loop behavior.
4. A v2 formal evaluation can be launched with explicit budget and API configuration.
5. The persisted report contains prompt metadata, calibration, A/B comparison, failure metrics, production quality gates, and release gate decision.
6. Stale v1 reports cannot pass the release gate.
7. Advisor remains disabled by default in normal runtime configuration.

If the real-model v2 run fails quality gates, task 2 can still complete as an implemented production-quality loop only if the failure is captured in the release gate as `BLOCKED` with concrete calibration and A/B evidence. In that case, the advisor is not production-ready, but the closed-loop mechanism is complete and ready for prompt/model tuning.
