# Memory Privacy And Compliance Plan

## Audit

Completed audit found these task 5 gaps:

- Sensitive detection is limited to a small credential regex.
- Export returns only memory items, with no manifest or redaction summary.
- Delete/forget are soft-delete only.
- No hard delete proof exists.
- No retention purge exists for deleted memories.
- Admin access is allowlist-based but not durably audited.
- Multi-tenant destructive-operation tests do not cover hard delete.

## Execution Plan

1. Add deterministic privacy scanner and redactor.
2. Wire scanner into memory classification and user memory updates.
3. Add export response manifest with redacted items and category counts.
4. Add hard-delete proof API using user-scoped repository deletion.
5. Add retention purge service path and migration support.
6. Add persistent admin access audit entity/repository/migration.
7. Add encryption-at-rest posture reporting for DB/disk/KMS confirmation.
8. Add tests for scanner, classifier, export, hard delete, retention, admin audit, migration SQL, posture, and controller routing.
9. Run targeted tests, then full backend and frontend validation.

## Review Checklist

- No raw PII appears in scan findings or deletion proof metadata.
- Destructive repository calls are user-scoped where user initiated.
- Retention purge only acts on already-deleted/retracted rows past the retention cutoff.
- At-rest encryption is reported as an explicit compliance posture, not silently assumed.
- Existing soft delete behavior remains backward compatible.
- Existing memory panel export callers still receive an `items` array.
