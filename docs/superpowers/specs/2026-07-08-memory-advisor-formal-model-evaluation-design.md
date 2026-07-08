# Memory Advisor Formal Model Evaluation Design

## Goal

Add a controlled formal evaluation path that runs the real LLM memory advisor against the active replay dataset with explicit API budget protection and a persisted evaluation report.

## Scope

This phase implements the ability to run a formal model-backed evaluation. It does not automatically run 4000 live API calls during normal tests or application startup. Live calls require an explicit opt-in environment variable and a configured API key.

## Architecture

Two layers are added:

- Main code:
  - `MemoryAdvisorFormalEvaluationService` estimates token and cost budget, blocks unsafe runs before API calls, invokes `MemoryAdvisorProductionQualityService`, and writes a JSON report.
  - It accepts replay cases and a `MemorySignalAdvisor`; it does not know where the replay dataset came from.
- Test/evaluation code:
  - `MemoryAdvisorFormalEvaluationIT` loads the active replay dataset, constructs the real `LlmMemorySignalAdvisor` with an actual OpenAI-compatible DeepSeek `ChatModel`, and calls the main formal evaluation service.
  - The integration test is disabled unless `MEMORY_ADVISOR_FORMAL_EVAL_ENABLED=true`.

This keeps normal `mvn test` deterministic and offline while preserving a single command for live formal evaluation.

## Budget Model

Before any model call, the formal evaluation service computes:

- selected case count;
- estimated input tokens using `JiTokenCountEstimator`;
- estimated output tokens using a configurable output-token-per-case value;
- estimated cost using `CostTrackingService` and the configured model name.

The run is blocked if:

- the requested live run is not explicitly enabled;
- no API key is present;
- selected cases are below the production minimum when full evaluation is required;
- estimated input tokens exceed the configured token cap;
- estimated cost exceeds the configured cost cap.

The service writes blocked preflight reports too, so failed attempts are auditable without spending API budget.

## Configuration

Runtime environment variables:

- `MEMORY_ADVISOR_FORMAL_EVAL_ENABLED`
  - Required value: `true`.
  - Default: `false`.
- `DEEPSEEK_API_KEY`
  - Required for live calls.
- `DEEPSEEK_MODEL`
  - Default: `deepseek-chat`.
- `DEEPSEEK_BASE_URL`
  - Default: `https://api.deepseek.com/v1`.
- `MEMORY_ADVISOR_FORMAL_EVAL_MAX_CASES`
  - Default: `4000`.
- `MEMORY_ADVISOR_FORMAL_EVAL_MAX_COST_YUAN`
  - Default: `20.0`.
- `MEMORY_ADVISOR_FORMAL_EVAL_MAX_INPUT_TOKENS`
  - Default: `2000000`.
- `MEMORY_ADVISOR_FORMAL_EVAL_OUTPUT_TOKENS_PER_CASE`
  - Default: `96`.
- `MEMORY_ADVISOR_FORMAL_EVAL_REPORT_DIR`
  - Default: `target/memory-advisor-formal-eval`.

## Report

The persisted JSON report contains:

- run status: `BLOCKED` or `COMPLETED`;
- run metadata: run ID, dataset version, model name, prompt version;
- budget estimate: cases, tokens, cost, caps;
- block reasons if blocked;
- production readiness report if completed.

Reports must not contain API keys or raw secrets.

## Execution Command

From `D:\ainotetest\backend`:

```powershell
$env:MEMORY_ADVISOR_FORMAL_EVAL_ENABLED="true"
$env:DEEPSEEK_API_KEY="<configured locally>"
$env:MEMORY_ADVISOR_FORMAL_EVAL_MAX_COST_YUAN="20"
mvn "-Dtest=MemoryAdvisorFormalEvaluationIT" test
```

The integration test exits without API calls when the explicit enable flag is absent.
