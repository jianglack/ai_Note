# Memory Chinese Replay And Advisor Evaluation Design

## Goal

Make memory governance quality measurable with realistic Chinese replay cases and an advisor-specific evaluation report. This keeps memory capture enterprise-oriented: controlled, testable, and measurable before enabling LLM-assisted capture in production.

## Scope

This slice is backend-only. It does not add frontend UI, database tables, migrations, or production LLM calls.

It adds:
- more Chinese replay cases for capture policy and candidate extraction;
- advisor replay quality metrics over the same case format;
- small policy hardening for common Chinese confirmation and assistant-feedback phrases.

## Architecture

`MemoryReplayEvaluationService` remains the policy/candidate gate. It answers: "Does the governed memory pipeline make the expected decision and candidate?"

`MemoryAdvisorReplayEvaluationService` is a new pure evaluator. It answers: "Given replay cases and a `MemorySignalAdvisor`, how well does the advisor classify capture/no-capture and memory type?" Tests use deterministic advisors; no real LLM call is made.

## Metrics

Policy replay metrics stay as:
- decision accuracy;
- should-not-remember precision;
- should-remember recall;
- candidate type accuracy;
- correction accuracy;
- reason coverage;
- signal coverage.

Advisor replay metrics add:
- availability rate;
- capture decision accuracy;
- false positive rate;
- false negative rate;
- memory type accuracy;
- failure details per case.

## Data

`backend/src/test/resources/memory/replay-eval-cases.json` becomes a mixed English and Chinese replay set. Chinese cases cover:
- delete all notes;
- common confirmations such as "可以" and "好的";
- explicit preferences;
- stable interaction style;
- style correction;
- project context;
- selected-note reference;
- RAG reference;
- one-off style instruction;
- assistant feedback such as "我喜欢这个回答，谢谢";
- sensitive secret;
- forget requests.

## Safety Rules

The advisor evaluator does not bypass hard-deny policy. It only scores advisor output against expected case labels. Production capture still flows through `MemoryCapturePolicy`, so selected notes, RAG references, one-off requests, secrets, forget requests, confirmations, and assistant feedback remain deny-side signals.

