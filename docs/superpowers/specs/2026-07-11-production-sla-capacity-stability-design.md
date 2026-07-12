# Production SLA, Capacity, And Stability Design

## 1. Decision

This work establishes an operator-grade performance qualification system for AiNote. The evidence model distinguishes two classes:

1. `local-production-like`: an isolated local backend using the production Spring profile, a dedicated PostgreSQL container, a dedicated Redis container, production-like pool limits, real HTTP, real JWT authentication, and real database transactions.
2. `external-staging`: a future execution of the same versioned workload against a separately approved staging topology.

The runner implemented in this work deliberately supports only `managed-local`. Remote execution has no accepted code path; enabling `external-staging` requires a separate reviewed operations wrapper with explicit non-production acknowledgement and database-audit credentials.

Neither class is labelled `production-proven`. A contractual production SLA requires a deployed production topology and observed production traffic. The local run is a release gate, not a substitute for production evidence.

## 2. Scope

The workload validates the stateful core that supports the memory system without paying for or depending on an external LLM during a long soak:

- 30% atomic short-term chat turn append: `POST /api/ai/chat/save`;
- 30% bounded chat history read: `GET /api/ai/chat/history?limit=100`;
- 20% long-term memory list: `GET /api/memories`;
- 20% paginated note list: `GET /api/notes?page=0&size=25`.

The operation selector uses the global scenario iteration number, not VU identity, so k6 scheduling cannot bias the mix. Every scored phase reports all four operation counts and must stay within one percentage point of the configured ratios.

External-model latency remains covered by the separate advisor and answer-quality formal evaluations and the existing `llm`/`sse_100` k6 profiles. Mixing paid provider latency into a multi-hour database soak would obscure local capacity and create uncontrolled cost.

## 3. Isolation And Safety

- Managed local mode starts disposable PostgreSQL and Redis containers and a dedicated backend port.
- Test users have a unique run prefix and exist only inside the disposable database.
- Compaction and Neo4j are disabled for this workload because provider and graph capacity are separate gates.
- Message bodies contain only synthetic run identifiers. Reports never contain JWTs, passwords, API keys, or message text.
- Remote targets are unsupported by the managed runner. A future staging wrapper must require both remote-target permission and explicit non-production confirmation.
- A production URL must remain unsupported until a separately reviewed production-run mode is implemented.
- Cleanup removes the backend process and disposable containers even when k6 or a quality gate fails.

## 4. Engineering SLOs

These are default engineering SLOs, configurable per approved environment:

| SLI | Default gate |
| --- | --- |
| Request availability | >= 99.9% |
| Functional check rate | >= 99.9% |
| Mixed request p95 | <= 500 ms |
| Mixed request p99 | <= 1,000 ms |
| Chat append p95 | <= 750 ms |
| Chat append p99 | <= 1,500 ms |
| History read p95 | <= 500 ms |
| History read p99 | <= 1,000 ms |
| Dropped iterations | 0 at the accepted capacity |
| Persisted chat rows | exactly 2 per successful append |
| Duplicate sequence numbers | 0 |
| Sequence continuity | true for every test user |
| Health probe failures | 0 |
| Process restarts during a phase | 0 |
| JVM heap maximum utilization | < 85% |
| Hikari pending connections | 0 at sampled boundaries |
| Live-thread drift during the post-capacity soak | <= 20 |

The report records p50, p95, p99, max, requests per second, operation counts, resource maxima, and every failed gate. Threshold changes must be version-controlled and reviewed; they cannot be supplied silently by an untracked dashboard.

## 5. Test Phases

### 5.1 Warm-up

- Run a short unscored warm-up.
- Confirm health and actuator access.
- Capture the process start time, JVM uptime, thread count, heap usage, and database-pool state.

### 5.2 Baseline

- Constant arrival rate at the expected low operating level.
- Establishes unloaded and low-load latency and verifies the report pipeline.
- Must pass every functional, latency, availability, resource, and integrity gate.

### 5.3 Capacity Discovery

- Runs independent constant-arrival-rate stages, by default 10, 25, 50, 100, and 200 iterations per second.
- Each stage has fresh k6 evidence and resource samples.
- A stage passes only when availability, checks, latency, dropped iterations, health, and resource gates pass.
- The highest passing stage is a demonstrated lower bound, not necessarily the physical maximum.
- If a later stage fails, the report records a capacity bracket between the highest pass and first fail.
- The recommended operating rate is at most 70% of the highest demonstrated passing rate and never above the configured expected peak without explicit approval.

### 5.4 Accepted-Capacity Gate

- Re-runs the recommended operating rate after discovery.
- Hard k6 thresholds are enabled.
- Data integrity is checked before proceeding to soak.

### 5.5 Soak

- Release soak default: 30 minutes.
- Pre-production certification default: 2 hours.
- Extended production-candidate soak: 24 hours when the deployment environment and operations window exist.
- Uses the accepted operating rate after the capacity staircase has expanded bounded server pools, samples resources throughout, verifies no restart, enforces post-cooldown thread drift, and performs final data-integrity checks.

The local execution in this work must complete at least the release soak. A 2-hour or 24-hour external staging run is required before claiming pre-production or production-candidate duration evidence.

## 6. Resource Evidence

The runner samples authenticated Actuator metrics while k6 is active:

- `process.uptime`;
- `jvm.memory.used` and `jvm.memory.max` with `area=heap`;
- `jvm.threads.live`;
- `process.cpu.usage` and `system.cpu.usage`;
- `hikaricp.connections.active` and `hikaricp.connections.pending`;
- `tomcat.threads.busy`;
- `jvm.gc.pause` where available.

Every sample contains only timestamped numeric values. Missing mandatory metrics fail the evidence gate rather than being converted to zero.

## 7. Data Integrity

Each successful append writes one USER and one AI row in one transaction. After each scored phase the runner queries the disposable PostgreSQL database and proves:

- total rows equal twice the successful append counter;
- every test user has unique `(user_id, sequence_number)` values;
- every user's range starts at zero and contains no gaps;
- no null sequence exists;
- no flush-failure metric was emitted.

Failed stages retain their evidence until the final report is written, then all disposable infrastructure is removed.

## 8. Machine-Readable Report

The final JSON report includes:

- schema version and environment classification;
- source commit and dirty-worktree flag;
- hardware/JDK/container topology;
- configured SLOs and workload mix;
- each phase's duration, target rate, actual throughput, latency percentiles, availability, checks, and dropped iterations;
- capacity lower bound, first failing rate, recommended operating rate, and headroom policy;
- resource maxima and drift;
- database-integrity evidence;
- quality-gate status and a flat failure list;
- timestamps and SHA-256 hashes of raw k6 summaries.

## 9. Release Decision

The task passes locally only when baseline, accepted-capacity, release-soak, resource, and integrity gates pass and the runner cleans up successfully. Capacity discovery may intentionally find a failing upper stage; that failure defines the bracket and does not fail the overall task if the accepted lower stage passes.

The final audit must state the exact evidence class. It must not convert `local-production-like` into a production SLA claim.
