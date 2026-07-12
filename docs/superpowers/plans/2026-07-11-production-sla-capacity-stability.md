# Production SLA, Capacity, And Stability Implementation Plan

## Phase 1: Contract And Safety

- [x] Add versioned engineering SLO defaults and workload ratios.
- [x] Restrict this runner to managed local targets; require a separate reviewed wrapper before any remote non-production execution.
- [x] Add disposable PostgreSQL, Redis, backend, users, and cleanup lifecycle.
- [x] Ensure reports and logs cannot contain tokens, passwords, keys, or message bodies.

## Phase 2: Workload And Evidence

- [x] Add a dedicated k6 mixed stateful workload for append, history, memory list, and notes.
- [x] Export operation counters, success rate, p50/p95/p99/max, throughput, and dropped iterations.
- [x] Add independent baseline, capacity-stage, accepted-capacity, and soak execution.
- [x] Hash and retain raw summaries and phase logs.

## Phase 3: Resource And Integrity Gates

- [x] Sample mandatory Actuator process, JVM, Hikari, and Tomcat metrics during every phase.
- [x] Detect health failures, process restarts, heap pressure, connection waiters, and thread drift.
- [x] Query the disposable database for exact row count, null/duplicate/gap checks, and per-user continuity.
- [x] Fail closed when mandatory evidence is unavailable.

## Phase 4: Orchestration And Reporting

- [x] Add a PowerShell runner with deterministic cleanup and configurable phase durations/rates.
- [x] Calculate the capacity lower bound, first failing rate, 70% headroom rate, and accepted operating rate.
- [x] Produce a normalized JSON report with a flat failure list and final quality gate.
- [x] Document local release-soak and configurable extended-soak commands; remote execution remains an environment-specific operations wrapper.

## Phase 5: Verification

- [x] Add static/unit checks for k6 profiles, safety guards, report schema, and cleanup behavior.
- [x] Execute syntax/static checks and focused backend regressions.
- [x] Run local baseline and capacity discovery against an isolated production-profile backend.
- [x] Run the accepted-capacity gate and at least a 30-minute release soak.
- [x] Verify cleanup leaves no test backend process or disposable container.
- [x] Complete final audit and distinguish local evidence from production proof.
