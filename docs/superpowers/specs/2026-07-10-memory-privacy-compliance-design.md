# Memory Privacy And Compliance Design

## Scope

Task 5 closes the memory privacy/compliance gaps for long-term memory:

- Strong deterministic PII and secret detection before memory capture/update.
- Redacted memory export with an explicit manifest and redaction summary.
- User-triggered hard delete with a durable deletion proof.
- Retention purge for already-deleted memories.
- Persistent admin access audit.
- Multi-tenant isolation tests for destructive operations.

## Current Gap

The existing system only has a coarse sensitive-content signal, user-scoped soft delete, and a simple export list. That is not enough for an enterprise memory system because it cannot prove destructive deletion, cannot report export redaction, cannot audit admin access durably, and does not exercise tenant boundaries around hard delete.

## Design

### PII Gate

Add `MemoryPrivacyService` as the deterministic privacy gate. It detects and redacts:

- credentials and tokens
- email addresses
- phone numbers
- China national ID numbers
- Luhn-valid payment card numbers
- address-like fields with address labels

The scanner returns only categories, spans, blocking flags, and token hashes. Raw sensitive values are never returned in scan metadata.

### Capture/Update Enforcement

`MemorySignalClassifier` uses the privacy scanner when deciding `sensitive_content`. User memory updates also run the scanner and fail fast when content is unsafe to store.

### Export

`GET /api/memories/export` returns a `MemoryExportResponse` containing:

- `items`
- `exportedAt`
- `itemCount`
- `redacted`
- `redactionSummary`
- `schemaVersion`

Exports are redacted by default through `app.memory.privacy.redact-exports`.

### Hard Delete

`POST /api/memories/hard-delete` accepts the same request shape as forget. The service resolves only the current user's memories, records redacted `HARD_DELETED` memory events with hashes, deletes rows by `user_id + id`, and returns a proof:

- request id
- user id
- deleted ids
- content hashes
- requested/completed timestamps
- status
- proof json

### Retention

Deleted/retracted memories older than `app.memory.privacy.deleted-memory-retention-days` can be purged. The purge records redacted `RETENTION_PURGED` events before deletion.

### Admin Audit

`AdminAccessGuard` writes `admin_access_audits` rows for allowed and denied admin checks. Audit failure must not bypass the access decision.

### Encryption Posture

Semantic memory content is used by search, recall, and embedding workflows. Application-layer encryption of the `content` column would break those paths unless a searchable-encryption design is introduced. For this task, at-rest encryption is treated as an infrastructure compliance requirement: database, disk, or KMS-backed encryption must be enabled outside the memory service, and the application exposes a guarded posture report with `atRestEncryptionRequired`, `atRestEncryptionConfirmed`, and `atRestEncryptionKeyRef`.

## Acceptance Gates

- PII scanner blocks representative email, phone, China ID, payment card, and token examples.
- Memory classifier emits `sensitive_content` for strong PII.
- User update rejects unsafe memory content.
- Export redacts stored sensitive values and reports category counts.
- Hard delete deletes only current-user rows and returns a proof.
- Retention purge removes only eligible deleted/retracted rows.
- Admin access checks persist allowed and denied audit records.
- Admin compliance posture reports whether at-rest encryption has been confirmed.
- Targeted tests and full backend/frontend validation pass.
