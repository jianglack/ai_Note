# Production SLA, Capacity, And Stability Preimplementation Review

## Verdict

`APPROVED_WITH_GATES`

The design is implementable, but the result may be called complete only for the evidence class actually executed.

## Findings Resolved Before Implementation

1. Existing performance coverage is broad but fragmented. It does not produce one capacity/stability report with integrity and resource evidence.
2. Existing `scheduler_soak` is not a representative stateful memory workload.
3. Existing k6 profiles use short fixed-duration checks and do not calculate an accepted operating rate with headroom.
4. Existing release verification checks thread drift for SSE only; it does not sample heap, CPU, Hikari, Tomcat, health, or uptime throughout a soak.
5. Running against the current `ainote` database would mix test and user data. Managed execution must use disposable infrastructure.
6. External LLM calls must stay outside the multi-hour core soak to prevent provider variability and uncontrolled paid usage from invalidating local capacity evidence.

## Mandatory Implementation Gates

- Remote execution is denied by default.
- Managed local mode uses the production Spring profile and disposable PostgreSQL/Redis containers.
- All test identities and data are isolated and deleted with the containers.
- Successful append counts are reconciled to exact database rows.
- Missing mandatory resource metrics fail the report.
- Capacity discovery records a bracket and does not hide an overloaded stage.
- The accepted-capacity and soak phases must independently pass hard thresholds.
- The final report states `local-production-like`, not `production-proven`.
- No source code, report, or command output may contain a real API key, JWT, or password.
- Cleanup is verified after both successful and failed runs.

## Non-Blocking Boundaries

- A single local JVM cannot prove horizontal-scaling capacity.
- A local container network cannot prove cloud network latency or managed-database behavior.
- The release soak is 30 minutes; 2-hour staging and 24-hour production-candidate evidence remain separate operational executions.
- Contractual SLA values require product and operations approval; this work supplies versioned engineering SLO defaults.

## Approval

Implementation may proceed. Final status must remain blocked if baseline, accepted-capacity, release-soak, integrity, resource, or cleanup gates fail.
