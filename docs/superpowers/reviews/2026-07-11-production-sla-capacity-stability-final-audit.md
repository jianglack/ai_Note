# Production SLA, Capacity, And Stability Final Audit

## Verdict

`PASS` for evidence class `managed-local`.

- Report status: `PASSED`
- Quality gate: `true`
- Production proven: `false`
- Release-soak duration: 30 minutes
- Final report SHA-256: `123450B70607510AA0B45A8C3893A8646F1EE3F347B910D19AA651687E38691E`
- Release-soak summary SHA-256: `2B6920352955DE5663043860176667CB5DA506CB1BF01800C3D3383DFDF957E4`
- Release-soak resource evidence SHA-256: `E18907D8FDB3A133458FF15CD9B1E429A57C8E5034ED907D7D91DAFA23286E97`

The engineering implementation and local production-like release gate are complete. This audit does not claim a contractual SLA or real-production proof.

## Executed Topology

- One JDK 17 backend instance using the Spring `prod` profile.
- JVM heap fixed at 512 MiB initial and 1,024 MiB maximum.
- Hikari maximum pool size 30.
- Disposable PostgreSQL 15 with pgvector.
- Disposable Redis 7.
- Neo4j and memory compaction disabled for this provider-independent stateful core gate.
- Sixty-four isolated users and synthetic request bodies.
- Existing port 8081, business database, and user data were not used.

## Workload Integrity

Every scored phase enforced and reported the configured mix:

- 30% atomic USER/AI turn append;
- 30% bounded history read;
- 20% long-term memory list;
- 20% paginated note list.

The final soak executed exactly:

- 45,000 total operations;
- 13,500 appends;
- 13,500 history reads;
- 9,000 memory-list reads;
- 9,000 note-list reads.

The selector uses the global scenario iteration number, so VU scheduling cannot bias the distribution.

## Capacity Evidence

All discovery stages passed:

| Target | Requests | Availability | p95 | p99 | Dropped |
| --- | ---: | ---: | ---: | ---: | ---: |
| 10 RPS | 450 | 100% | 39.58 ms | 42.82 ms | 0 |
| 25 RPS | 1,126 | 100% | 40.30 ms | 48.96 ms | 0 |
| 50 RPS | 2,251 | 100% | 39.19 ms | 46.41 ms | 0 |
| 100 RPS | 4,501 | 100% | 40.97 ms | 60.07 ms | 0 |
| 200 RPS | 9,001 | 100% | 42.98 ms | 65.18 ms | 0 |

- Demonstrated single-instance lower bound: at least 200 RPS.
- Physical ceiling: not found; the result must not be restated as a 200 RPS maximum.
- Headroom rule: 70% of demonstrated passing capacity.
- Configured expected peak: 25 RPS.
- Accepted operating rate: 25 RPS.
- Accepted-capacity confirmation: 3,001 requests, 100% availability, p95 37.14 ms, p99 43.58 ms, zero dropped iterations.

## Thirty-Minute Stability Evidence

- Target and actual rate: 25 RPS / 24.9986 RPS.
- Requests: 45,000.
- Availability: 100%.
- Functional check rate: 100%.
- Mixed latency: p95 36.18 ms, p99 43.67 ms.
- Append latency: p95 40.74 ms, p99 51.12 ms.
- History latency: p95 25.02 ms, p99 31.17 ms.
- Dropped iterations: 0.
- Health failures: 0.
- Resource sample errors: 0.
- Process uptime monotonic: true.
- Maximum heap utilization: 43.42%, below the 85% gate.
- Process CPU p95: 1.35%, below the 85% gate.
- Maximum Hikari pending connections: 0.
- Live-thread drift after capacity warm-up and soak cooldown: 0.

## Data Integrity

The final database audit included every successful append from warm-up through soak:

- Expected rows: 39,448.
- Persisted rows: 39,448.
- Null sequences: 0.
- Duplicate sequences: 0.
- Users with sequence gaps: 0.
- Head mismatches: 0.

The soak itself completed 13,500 successful appends and therefore contributed exactly 27,000 USER/AI rows.

## Cleanup And Security

- Temporary token file removed.
- Isolated backend stopped.
- Disposable PostgreSQL and Redis containers removed.
- No test container or isolated backend process remained.
- The existing backend remained `UP`; the frontend login route returned HTTP 200.
- Reports contain numeric samples and hashes, not JWTs, passwords, API keys, or message bodies.

## Regression Evidence

- PowerShell parser: passed.
- k6 inspect: passed with k6 1.2.3.
- SLA asset tests: 4 passed.
- Full backend regression with explicit temporary PostgreSQL: 1,008 tests, 0 failures, 0 errors, 0 skipped.
- Frontend: 23 files, 79 tests passed.
- Frontend production build: passed.
- `git diff --check`: passed for the task files; only repository line-ending warnings were emitted.

## Review Corrections

The review process rejected intermediate evidence until these issues were fixed:

1. PowerShell CSPRNG API compatibility.
2. k6 1.2 summary-export schema compatibility.
3. Tomcat MBean metric registration.
4. Normal Tomcat thread-pool expansion incorrectly treated as a leak during discovery.
5. VU identity correlated with operation selection and biased workload ratios.

The final accepted run occurred only after operation selection was changed to global iteration-based deterministic distribution and operation ratios became explicit phase gates. Earlier reports are diagnostic artifacts and are not release evidence.

## Remaining Production Boundary

This task has completed the reusable engineering gate and its local production-like execution. The following require an external deployment environment and are not asserted by this report:

- horizontal multi-instance capacity;
- cloud network and load-balancer latency;
- managed database behavior and production connection limits;
- a 2-hour staging certification soak;
- a 24-hour production-candidate soak;
- observed monthly production availability and error budget.

Those runs must use an approved non-production staging topology first, preserve the same SLO version and workload mix, and publish a separate report labelled with the actual environment. Only real production observations may set `productionProven=true`.
