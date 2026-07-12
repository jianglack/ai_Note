# Memory Production Observability Design

## Scope

Task 6 upgrades memory observability from audit logs and offline tests to production metrics. The goal is to expose operator-ready indicators for long-term memory capture, advisor health, retrieval, context injection, user deletion, wrong-write feedback, and latency.

## Metrics Contract

All production counters are process-local Micrometer metrics and are also surfaced through an admin snapshot endpoint for the frontend dashboard. Persistent audit events remain the source of forensic detail, while Micrometer remains the source of operational trend data.

Required operator indicators:

- Memory write rate: count of created, reinforced, and superseded write outcomes.
- Memory rejection rate: denied or skipped capture decisions divided by total capture decisions.
- Advisor failure rate: unavailable, parse-failed, or model-failed advisor results divided by advisor calls.
- User deletion rate: soft delete, forget, and hard delete requests divided by capture decisions.
- Retrieval hit rate: retrieval requests with at least one semantic or episodic memory divided by all retrieval requests.
- Context injected memory count: number of semantic and episodic memories injected into model context.
- Wrong-write feedback rate: `wrong_memory` and `should_not_remember` feedback divided by all memory feedback.
- p95 latency: capture and retrieval latency p95, in milliseconds.

## Instrumentation Points

- `MemoryOrchestrator`: records capture decisions, rejection reasons, candidate count, write count, and end-to-end capture latency.
- `MemoryWriteService`: records write outcomes and privacy rejection skips.
- `LlmMemorySignalAdvisor`: records advisor requests and unavailable/failure outcomes.
- `MemoryRetrievalService`: records retrieval requests, hit/miss, returned memory counts, fallback reason, and retrieval latency.
- `ContextAssembler`: records semantic and episodic memory injection counts.
- `MemoryControlService`: records user-initiated soft delete, forget, hard delete, and retention purge outcomes.
- `MemoryReviewService`: records feedback totals and wrong-write feedback.

## Exposure

- Prometheus: exported through the existing `/actuator/prometheus` pipeline.
- Admin API: `GET /api/admin/memory-metrics`, guarded by `AdminAccessGuard`.
- Frontend: the existing admin metrics dashboard includes a memory production section that refreshes with the same cadence as agent metrics.

## Quality Gates

- Metrics service has deterministic unit coverage for counters, rates, p95, and bounded latency samples.
- Admin endpoint verifies authorization guard and JSON fields.
- Production services verify they call metrics on capture, retrieval, delete, advisor failure, and feedback paths.
- Full backend and frontend test suites continue to pass.
