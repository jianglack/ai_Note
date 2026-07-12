# Memory Production Observability Plan

## Review

Existing state:

- Actuator and Prometheus are already configured.
- `AgentMetricsService` exposes general agent metrics and the frontend has an admin metrics dashboard.
- Memory production paths currently emit logs and audit events, but do not expose dedicated Prometheus metrics or a memory metrics panel.

Gap:

- Operators cannot monitor memory write rate, rejection rate, advisor failure rate, deletion rate, retrieval hit rate, injected memory count, wrong-write feedback rate, or p95 latency from a single production dashboard.

## Design Decisions

- Add a dedicated `MemoryMetricsService` instead of mixing memory metrics into `AgentMetricsService`.
- Keep metric tags low-cardinality: outcome, reason, mode, result, type, and source only.
- Keep admin snapshot in memory for live rates; forensic detail stays in `memory_events`.
- Compute p95 from bounded in-process samples so dashboard values are deterministic in tests and independent of Micrometer backend behavior.

## Execution Steps

1. Add memory metrics snapshot model and service.
2. Add admin endpoint and frontend API client.
3. Instrument capture, write, advisor, retrieval, context injection, feedback, and deletion paths.
4. Extend the admin metrics dashboard with memory production indicators.
5. Add targeted backend tests and run full verification.

## Acceptance Criteria

- `/api/admin/memory-metrics` returns all required task-6 indicators.
- Prometheus includes memory-specific counters, summaries, and timers.
- Dashboard shows memory production metrics without blocking the existing agent metrics view.
- Metrics do not include raw memory content, user text, or PII.
- Backend and frontend verification passes.
