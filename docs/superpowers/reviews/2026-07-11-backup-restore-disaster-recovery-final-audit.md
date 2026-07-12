# Backup, Restore, and Disaster-Recovery Final Audit

## Decision

**PASSED for managed-local open-source readiness.** The implementation meets the design acceptance criteria. It does not claim production disaster-recovery proof.

## Delivered controls

1. PostgreSQL is documented and implemented as the sole authoritative backup source; database-backed media is included.
2. Redis recovery is start-empty/re-authenticate, followed by JWT secret rotation.
3. Neo4j is rebuilt from PostgreSQL, including the previously omitted `note_concepts` projection.
4. Backups use PostgreSQL custom format, AES-256-GCM chunk authentication, manifest HMAC-SHA256 with a domain-separated key, and SHA-256 storage checks.
5. Keys and application secrets are excluded from packages and reports.
6. Restore is fail-closed for missing/tampered metadata, failed AEAD, invalid dump, non-empty target, Flyway mismatch, table-count mismatch, or sequence-state mismatch.
7. The destructive drill is limited to generated `ainote-dr-*` containers and cleans up in `finally`.
8. A maintainer runbook defines backup, restore, post-restore, retention, and incident evidence procedures.

## Final drill evidence

- Report: `backend/target/backup-restore-drill/ainote-dr-20260711T070802Z-64316/backup-restore-drill-report.json`
- Report SHA-256: `43B4EDA4136FE59A02C7D3F37954CC5C5F868AD163169485AB62D32AAEBD2C5D`
- Status: `PASSED`
- Quality gate: `true`
- Evidence class: `managed-local`
- Production proven: `false`
- Flyway version: `60`
- Exact inventory: 42 tables and 10 sequences
- Source removed before restore: `true`
- Database RTO: 27,570 ms
- RPO interval at restore start: 6.383 seconds
- Representative checks: user, note, media, vector, long-term memory, short-term memory, review, and audit all `true`
- Manifest HMAC authenticated: `true`
- Artifact AEAD authenticated: `true`
- Inventory matched: `true`
- Authenticated corruption rejected: `true`
- Corrupt target untouched: `true`
- Non-empty target rejected: `true`
- Containers removed: `true`
- Ephemeral drill key destroyed: `true`

The corruption case recomputed the artifact SHA, manifest SHA, and a valid manifest HMAC before restore. Restore then failed specifically with `encrypted backup authentication failed`; the empty target remained untouched. This proves the negative gate reaches AES-GCM authentication rather than passing only because of a stale checksum.

## Regression evidence

- Backend full regression after final hardening: 1022 tests, 0 failures, 0 errors, 1 expected skip (`MemoryAnswerQualityFormalEvaluationIT`, gated by `MEMORY_ANSWER_QUALITY_FORMAL_EVAL_ENABLED=true`).
- Backup/restore, streaming crypto, and Neo4j rebuild targeted tests passed after the final security hardening.
- Frontend: 46 test files, 79 tests passed, 0 failed.
- Frontend production build passed.
- Main backend remained `UP`; no existing AiNote data container was used as a destructive target.

## Problems found and corrected during execution

1. Neo4j disaster rebuild omitted persisted concepts. The rebuild now projects `note_concepts` grouped by note.
2. Windows PowerShell 5.1 lacks `AesGcm`. A Java 17 streaming helper replaced the non-runnable implementation.
3. Expected Docker “not found” probes were terminal under PowerShell strict error handling. The namespace check now handles that result explicitly.
4. Windows native argument quoting corrupted JSON fixtures. PostgreSQL JSON constructors removed shell-dependent quoting.
5. Scalar query output and ordered dictionaries were mishandled under strict mode. Inventory utilities now normalize both forms.
6. PostgreSQL boolean text was parsed only as `t`, producing symmetric but inaccurate sequence evidence. `t`, `true`, and `1` are now handled and verified.
7. SHA-256 alone did not authenticate the manifest. Domain-separated HMAC-SHA256 was added and is verified before parsing metadata.
8. The running backend locked the normal Maven repackage path. A separate temporary build produced the fat JAR, which was verified and restored to the standard path without stopping the service.

## Residual boundaries

The local project still cannot prove object-store immutability, off-host replication, KMS/HSM custody, continuous WAL/PITR, cross-region failover, or a production RTO/RPO. Those are deployment-platform responsibilities and remain explicit non-claims, not hidden failures of this managed-local gate.
