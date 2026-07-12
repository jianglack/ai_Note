# Backup, Restore, and Disaster-Recovery Implementation Plan

## Objective

Deliver a secure, reproducible managed-local backup and restore gate for AiNote, including a real source-destruction drill and machine-readable evidence.

## Work sequence

1. Audit data authority and record PostgreSQL, Redis, Neo4j, media, release, and secret boundaries.
2. Fix Neo4j rebuild so PostgreSQL `note_concepts` are not omitted.
3. Add a shared PowerShell backup library and Java 17 streaming crypto helper with native-command checking, SHA-256, chunked AES-256-GCM, Docker PostgreSQL validation, exact table inventory, and secret-safe reporting.
4. Add `backup-ainote.ps1` to create the versioned encrypted package.
5. Add `restore-ainote.ps1` to validate, decrypt, preflight, restore, verify, and report.
6. Add `run-backup-restore-drill.ps1` to create a disposable source, install the current schema, seed representative data, back it up, remove it, create a fresh target, restore, verify, test corruption rejection, and clean up.
7. Add Java asset tests for package contracts, fail-closed controls, data classifications, and graph rebuild coverage.
8. Run targeted tests, the actual drill, backend regression, frontend regression, and final review.

## Required evidence

- Encrypted backup directory and checksums.
- Successful restore report with source/target inventory equality.
- Drill report with source destruction evidence, corruption rejection, non-empty target rejection, RPO/RTO measurements, and explicit non-production classification.
- Test logs showing targeted and full regression results.

## Stop conditions

Execution stops and reports failure if Docker is unavailable, a target name is outside the drill namespace, encryption key validation fails, manifest validation fails, ciphertext authentication fails, `pg_restore` preflight fails, target is non-empty, restore exits non-zero, or post-restore inventory differs.

## Rollback

No existing database is modified by the drill. Code rollback consists only of reverting the newly added scripts, documentation, tests, and scoped Neo4j rebuild fix. Generated evidence lives under `backend/target/backup-restore-drill/` and is not source data.
