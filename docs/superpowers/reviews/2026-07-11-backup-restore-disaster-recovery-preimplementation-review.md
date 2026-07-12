# Backup and Restore Pre-implementation Review

## Decision

Approved to implement with the controls below. No destructive operation may target `ainote-postgres`, `ainote-redis`, `ainote-neo4j`, or any caller-supplied container during the automated drill.

## Findings resolved by design

1. **Authority ambiguity**: PostgreSQL is authoritative; media is embedded in PostgreSQL; Redis and Neo4j are not co-equal backup sources.
2. **Session safety**: Redis is a token allow-list. Empty recovery invalidates sessions rather than reviving revoked tokens. Re-login and JWT rotation are mandatory runbook steps.
3. **Graph loss**: existing graph rebuild omits persisted `note_concepts`; implementation must fix and test this before Neo4j can be classified as reconstructable.
4. **Plaintext exposure**: a raw dump is unacceptable as the delivered package. AES-256-GCM is mandatory and the key is environment-only.
5. **False restore proof**: copying a file is not a restore. Acceptance requires source removal, a new target, `pg_restore`, exact inventory comparison, and representative row validation.
6. **Unsafe overwrite**: restore rejects non-empty targets by default and destructive override is not used by the drill.
7. **Corruption handling**: both ciphertext SHA-256 and AES-GCM authentication must fail before database mutation.
8. **Secret leakage**: reports and manifests may name environment-variable references but may not contain their values.

## Residual risks accepted for managed-local evidence

- No object-store immutability or external signature is available locally.
- Logical dumps provide no continuous point-in-time recovery.
- Neo4j rebuild remains per-user and must be orchestrated after database recovery.
- Local measured RTO is not a production SLA.

## Review gate

Implementation may proceed only with generated container names under `ainote-dr-*`, unconditional cleanup in `finally`, and a final report that cannot claim `productionProven=true`.
