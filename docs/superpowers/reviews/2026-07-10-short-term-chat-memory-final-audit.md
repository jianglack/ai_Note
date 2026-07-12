# Short-Term Chat Memory Final Audit

## Verdict

`PASS`

- Quality gate: `true`
- Scope: Task 8, short-term chat memory reliability and pressure validation
- Database evidence: PostgreSQL 15, Flyway V60, real JPA transactions
- Review result: no release-blocking findings remain in the agreed Task 8 scope

## Requirement Traceability

| Requirement | Implementation evidence | Verification evidence | Result |
| --- | --- | --- | --- |
| Never load the complete transcript into the model | The model path reads only untrimmed tail rows with `model-window-max-messages`; token trimming remains a second bound | With 10,000 history rows, the model loaded exactly 128 rows | PASS |
| Stable append writes | `chat_memory_heads` allocates sequence numbers while locked; a unique `(user_id, sequence_number)` index is the final invariant; each retry uses a new transaction | A real `/chat/save` request persisted two rows at sequences 0 and 1; append metrics increased by one turn and two messages | PASS |
| Concurrent writes are safe | Sequence allocation and turn persistence occur in one database transaction under a pessimistic head-row lock | 32 concurrent turns persisted 64 of 64 rows, with zero duplicate sequences and a continuous 0..63 sequence | PASS |
| UI history and model context are separated | UI pagination includes trimmed USER/AI rows; the model query excludes trimmed rows; trimming marks rows instead of deleting them | Fifty UI pages returned all 10,000 rows with zero duplicate IDs while the model saw only 128 rows | PASS |
| Long conversations compact reliably | Compaction ranges are persisted idempotently, leased, bounded by message and character limits, retried with backoff, and dead-lettered at the configured limit; model calls run outside the claim transaction; completion and failure updates verify the current attempt owns the lease | Fault injection proves two model failures followed by a successful third attempt; a stale worker cannot overwrite a reclaimed job; source messages remain intact | PASS |

## Migration Audit

- V60 adds `trimmed_at`, normalizes every historical sequence, enforces non-null sequences, and creates the unique sequence invariant.
- V60 creates durable head and compaction-job tables plus the model-window and job indexes.
- The V59-to-V60 upgrade integration test preserves all rows while repairing null and duplicate sequences.
- Production-like local database migration preserved 1,231 rows; null sequences changed from 70 to 0; duplicate sequences remained 0; 162 head rows were initialized.
- Flyway advanced from V59 to V60 in 0.248 seconds in the local environment.
- The migration intentionally does not delete source chat history.

## Automated Verification

- Focused Task 8 tests: 37 passed.
- Full backend regression with explicit temporary PostgreSQL databases: 1,004 tests, 0 failures, 0 errors, 0 skipped.
- Frontend regression: 79 tests across 23 files passed.
- Frontend production build: passed.
- Fresh-database Flyway replay: all 60 migrations passed.
- Schema validation and real V59-to-V60 upgrade tests: passed.
- `git diff --check`: passed; only line-ending conversion warnings were emitted.

## Capacity Report

Report: `backend/target/short-term-memory-eval/short-term-memory-reliability-report.json`

- Status: `PASSED`
- History rows: 10,000
- Model rows loaded: 128
- Model-window read p50: 12 ms
- Model-window read p95: 22 ms
- UI rows scanned: 10,000 in 50 pages
- UI duplicate IDs: 0
- Concurrent turns: 32
- Persisted concurrent messages: 64 of 64
- Duplicate sequences: 0
- Sequence continuity: `true`
- Concurrent write duration: 1,363 ms
- SHA-256: `6C909E9F821EE31B56DA8B9EA5B9C8D501E9FAFE4D96D3999C8E42025FF454B7`

These latency values are a reproducible local release baseline, not a production SLA. Production capacity planning must rerun the same gate with production topology, connection-pool limits, retention volume, and expected concurrency.

## Runtime Evidence

- Backend health: `UP` on port 8081 using JDK 17.
- A temporary isolated account sent one real `/api/ai/chat/save` turn.
- Prometheus deltas: `chat_memory_append_total +1`, appended-message count `+1`, appended-message sum `+2`.
- Database result: two rows, minimum sequence 0, maximum sequence 1.
- Cleanup result: zero temporary users, memories, heads, and compaction jobs remained.

## Operations And Rollback

- Emergency write-path rollback: set `MEMORY_CHAT_HISTORY_WRITE_MODE=legacy_rewrite`.
- Compaction can be disabled independently with `MEMORY_CHAT_HISTORY_COMPACTION_ENABLED=false`.
- Flush retry count/delay, model-window size, compaction batch size, lease, retry limit, source-message limit, source-character limit, and polling interval are configuration, not hard-coded policy.
- V60 constraints and head tables remain after a write-mode rollback; dropping them is not part of the runtime rollback procedure.
- Prometheus exposes load, append, trim, flush retry/failure, and compaction result/latency signals without recording message bodies.

## Residual Product Boundary

Task 8 intentionally keeps the current user-scoped conversation identity and does not introduce multi-thread conversation switching or a separate physical transcript table. If multi-thread chat becomes a product requirement, add a conversation identifier and migrate transcript ownership as a separate reviewed change. This does not weaken the verified bounded-context, append, concurrency, separation, or compaction guarantees in the current scope.
