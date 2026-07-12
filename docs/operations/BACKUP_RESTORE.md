# AiNote Backup and Restore Runbook

## Scope

This runbook covers the Docker PostgreSQL deployment shipped with AiNote. PostgreSQL is authoritative. `note_media` is inside PostgreSQL. Redis is ephemeral session state and Neo4j is a derived read model.

## Prerequisites

- Docker Engine is available.
- The PostgreSQL container uses PostgreSQL 15 with pgvector.
- Java 17 is available. Override `-JavaPath` when it is not at `D:\Java\jdk17\bin\java.exe`.
- PowerShell 5.1 or newer is available.
- The operator can place the application in a write-quiescent maintenance window.
- The encryption key is held separately from the backup destination.

## Create the encryption key

Generate a new 32-byte key in the current process. Do not write it into the repository, command history, package, ticket, or report.

```powershell
$key = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($key) } finally { $rng.Dispose() }
$env:AINOTE_BACKUP_KEY_BASE64 = [Convert]::ToBase64String($key)
[Array]::Clear($key, 0, $key.Length)
```

In a deployed environment, inject this variable from a secret manager. Retain the matching key version for as long as any package encrypted with it must remain recoverable.

## Create a verified backup

Pause application writes, then run:

```powershell
.\scripts\backup-ainote.ps1 `
  -PostgresContainer ainote-postgres `
  -Database ainote `
  -DatabaseUser ainote `
  -OutputRoot E:\ainote-backups
```

Success prints `status=COMPLETE` and the package path. A complete package contains:

- `postgres.dump.aesgcm`
- `manifest.json`
- `manifest.hmac`
- `checksums.sha256`

The script rejects a database that changes during inventory capture. Resume writes only after the script completes. Copy the complete encrypted directory to off-host storage and apply the storage platform's retention, immutability, and access controls.

## Restore

Provision the matching Git release and an empty PostgreSQL 15 + pgvector container. Set the original backup key in `AINOTE_BACKUP_KEY_BASE64`, then run:

```powershell
.\scripts\restore-ainote.ps1 `
  -PackageDirectory E:\ainote-backups\backup-YYYYMMDDTHHMMSSZ-ID `
  -PostgresContainer ainote-postgres-restore `
  -Database ainote `
  -DatabaseUser ainote `
  -OutputRoot E:\ainote-restore-reports
```

The default refuses any non-empty target. Do not use `-AllowNonEmptyTarget` in routine recovery; it is an explicit destructive override for an operator-controlled replacement database.

The restore passes only after manifest HMAC, encrypted artifact SHA-256, AES-GCM authentication, `pg_restore --list`, Flyway version, every table count, and every sequence state match.

## Post-restore order

1. Provision external secrets from the deployment's secret manager.
2. Rotate `JWT_SECRET`.
3. Start Redis empty; require all users to sign in again.
4. Start the backend against the restored PostgreSQL database.
5. Start Neo4j empty.
6. Invoke `POST /api/graph/rebuild` once for each user using an authenticated, tenant-scoped request.
7. Verify health, authentication, representative notes, media, search, long-term memory, and chat history.
8. Start the frontend and reopen traffic.

## Run the managed-local drill

```powershell
.\scripts\run-backup-restore-drill.ps1
```

The drill uses only generated `ainote-dr-*` containers. It creates a source, migrates it to the current Flyway version, seeds representative data, backs it up, removes the source, restores a new target, verifies data, tests authenticated corruption rejection, tests non-empty target rejection, and removes every temporary container and key.

Evidence is written under `backend/target/backup-restore-drill/`. A pass is managed-local evidence and always reports `productionProven=false`.

## Scheduling and retention

- Start with a daily verified logical backup and a scheduled restore drill.
- Keep at least one encrypted off-host copy.
- Alert when backup creation, off-host replication, checksum verification, or a restore drill fails.
- Define retention and key deletion together. Deleting the only matching key makes a package unrecoverable.
- RPO equals the age of the latest successful recoverable backup. This implementation does not provide WAL archiving or point-in-time recovery.

## Incident evidence

Preserve the package manifest, restore report, application release commit, operator timeline, and external storage audit logs. Never attach the backup key or decrypted dump to incident records.
