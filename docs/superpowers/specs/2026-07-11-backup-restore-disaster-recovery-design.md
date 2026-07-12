# AiNote Backup, Restore, and Disaster-Recovery Design

## 1. Purpose

This design defines a reproducible backup and restore boundary for the open-source AiNote deployment. It proves the mechanics in isolated managed-local containers. It does not claim production disaster-recovery certification.

## 2. Data authority

| Component | Classification | Backup treatment | Recovery treatment |
| --- | --- | --- | --- |
| PostgreSQL 15 + pgvector | Authoritative | Required logical custom-format dump | Restore before application startup |
| `note_media` payloads | Authoritative, stored in PostgreSQL | Included in PostgreSQL dump | Verified by table inventory and drill fixtures |
| Neo4j | Derived read model | Excluded from authoritative package | Start empty and rebuild from PostgreSQL |
| Redis | Ephemeral token allow-list | Excluded | Start empty; all users must sign in again |
| Flyway migrations, prompts, application code | Release-controlled | Excluded from data package | Restore the matching Git release first |
| Passwords, API keys, JWT secret, backup key | External secrets | Never written to package or report | Provision from secret management; rotate JWT secret after a disaster |
| `target/` evaluations and local logs | Generated evidence | Excluded | Regenerate when required |

PostgreSQL is the sole authoritative data backup. This avoids inconsistent multi-database snapshots. Neo4j is recoverable only if every graph projection, including persisted note concepts, is reconstructed from PostgreSQL.

## 3. Package contract

Each package is a directory containing:

- `manifest.json`: schema version, backup identity, source compatibility, Flyway version, exact per-table row inventory, component classifications, and artifact metadata.
- `manifest.hmac`: HMAC-SHA256 authentication of the manifest using a domain-separated key derived from the backup key.
- `postgres.dump.aesgcm`: PostgreSQL custom-format dump encrypted with AES-256-GCM.
- `checksums.sha256`: detached SHA-256 values for the encrypted artifact and manifest.

The package never contains the encryption key, database password, JWT secret, API key, access token, or raw command line containing a secret.

The backup key is supplied through `AINOTE_BACKUP_KEY_BASE64` by default and must decode to exactly 32 bytes. AES-GCM authenticates ciphertext, HMAC-SHA256 authenticates the manifest, and SHA-256 detects storage corruption before restore. Off-host key custody remains a deployment responsibility.

## 4. Backup consistency

`pg_dump --format=custom --no-owner --no-acl` obtains a transactionally consistent PostgreSQL snapshot without stopping the application. The source remains read-only from the backup tool's perspective. Exact table counts and the current Flyway version are captured before the dump and checked after restoration.

For write-heavy production systems, table counts can change between inventory capture and dump. The script therefore records both the pre-dump and dump timestamps and treats inventory as an acceptance contract only when the caller supplies a maintenance/quiescence window. The automated drill uses a quiescent isolated source.

## 5. Restore safety

Restore is fail-closed:

1. Validate manifest SHA-256 and HMAC before trusting its schema or artifact name.
2. Validate artifact SHA-256 and AES-GCM authentication.
3. Validate `pg_restore --list` before touching the target.
4. Require a running Docker PostgreSQL target.
5. Reject a non-empty target database unless the caller explicitly supplies the destructive override.
6. Restore with `--exit-on-error --no-owner --no-acl`.
7. Compare Flyway version and exact row counts against the manifest.
8. Write a machine-readable restore report.

Normal restore never deletes the source database or an existing target. The destructive drill creates and removes only containers whose generated names start with `ainote-dr-`.

## 6. Derived-state recovery

Redis starts empty. Because Redis is an allow-list, existing JWTs fail validation; users must sign in again. Disaster recovery also requires JWT secret rotation so tokens from the pre-disaster environment cannot be accepted elsewhere.

Neo4j starts empty. After PostgreSQL and the application are available, an authenticated user invokes `POST /api/graph/rebuild`. The rebuild must restore folders, notes, tags, schedules, wiki links, and `note_concepts` relationships from PostgreSQL. Multi-user deployments invoke the endpoint once per user or provide an administrator-controlled batch orchestration outside this package.

## 7. RPO and RTO

- RPO is the interval since the last successful verified backup. This design does not implement continuous WAL archiving or point-in-time recovery.
- RTO is measured from restore start through PostgreSQL validation in the local drill. Application, Redis, Neo4j, DNS, and external secret provisioning are reported separately and are not hidden inside the database RTO.

Recommended starting policy for maintainers is a daily full logical backup, at least one encrypted off-host copy, and scheduled restore drills. Operators must set policy based on their own data-loss tolerance.

## 8. Acceptance criteria

The item passes only when all conditions hold:

1. Backup package is encrypted and contains no key or application secret.
2. Untampered package restores into a newly created isolated PostgreSQL container.
3. The source container is removed before target restore.
4. Flyway version and exact per-table row counts match.
5. Representative user, note, media, vector, memory, chat, and audit rows survive.
6. A one-byte-corrupted artifact is rejected before target mutation.
7. A non-empty target is rejected without explicit override.
8. Redis and Neo4j recovery semantics are documented and tested at the code/asset level.
9. Generated report states `evidenceClass=managed-local` and `productionProven=false`.

## 9. Explicit non-claims

This work does not prove cloud snapshot integration, cross-region replication, object-lock retention, HSM/KMS key custody, continuous point-in-time recovery, or production RTO/RPO. Those require the eventual deployment platform.
