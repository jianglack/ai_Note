# Memory Human Review Loop Design

## Goal

Build the production human review loop for long-term memory failures: user feedback, review queue, admin decisioning, audit trail, and replay candidate export.

## Current State

The memory system already has user controls and governance metadata:

- `/api/memories` lists user-visible memories.
- `/api/memories/{id}` can update a memory.
- `/api/memories/{id}` delete soft-deletes a memory.
- `/api/memories/forget` supports user-requested deletion.
- `/api/memories/events` exposes the user-scoped audit ledger.
- `semantic_memories` stores source trace, source message ids, evidence excerpt, metadata, status, supersession, and access fields.
- `memory_events` records create/update/delete/capture events.
- Replay datasets and test-side sample review services exist, but production feedback does not feed them.

The missing enterprise loop is that a user cannot report a wrong memory as structured feedback, an admin cannot review that feedback as a durable work queue, and reviewed failures cannot be exported as replay candidates for future release gates.

## Scope

In scope:

- User feedback API for a specific memory.
- Durable `memory_review_cases` table.
- Service layer for feedback creation, admin listing, admin decisions, and replay candidate export.
- Admin endpoints protected by `AdminAccessGuard`.
- Audit events for feedback and review decisions.
- Replay candidate JSON generation from reviewed cases.
- Tests for migration SQL, service behavior, controller routing, admin guard usage, and replay export.

Out of scope for task 3:

- Full frontend review dashboard. That belongs to task 4 frontend explainability/productization.
- Automatic insertion into `src/test/resources/memory/replay-eval-cases.json` from a live API. The API exports reviewed candidates; promotion into the committed replay file remains a deliberate release step.
- PII redaction engine improvements. Those belong to task 5 privacy/compliance.
- Answer quality evaluation. That belongs to task 7.

## Data Model

Add `memory_review_cases`.

Required fields:

- `id`: primary key.
- `user_id`: owner of the memory and feedback.
- `memory_id`: reviewed memory id.
- `feedback_type`: normalized user feedback type.
- `user_comment`: optional user explanation.
- `expected_content`: optional corrected memory content.
- `expected_memory_type`: optional corrected memory type.
- `expected_capture_allowed`: optional expected capture decision.
- `status`: review status.
- `reviewer_id`: admin/developer reviewer id.
- `reviewer_decision`: normalized decision.
- `reviewer_comment`: reviewer explanation.
- `replay_case_id`: stable generated replay candidate id.
- `replay_case_json`: generated replay candidate payload.
- `manifest_json`: provenance and review metadata.
- `memory_before_json`: snapshot of memory state at feedback time.
- `source_context_json`: snapshot of trace/message/evidence/source fields.
- `policy_snapshot_json`: snapshot of policy/advisor metadata from memory metadata.
- `created_at`, `updated_at`, `reviewed_at`.

Indexes:

- `(user_id, created_at DESC)` for user-scoped lookup.
- `(status, created_at)` for review queue.
- `memory_id` for memory-centered lookup.
- unique `replay_case_id` for replay export stability.

## Status Model

Review case statuses:

- `pending_review`: user feedback has been filed.
- `rejected`: reviewer rejected the feedback.
- `actioned`: reviewer accepted feedback and applied a memory action, but did not approve replay export.
- `approved_for_replay`: reviewer approved this case as a replay candidate.
- `duplicate`: reviewer marked it as a duplicate signal.

Admin decisions:

- `reject_feedback`: no memory change.
- `disable_memory`: set memory status to `disabled`.
- `delete_memory`: set memory status to `deleted`.
- `update_memory`: patch memory content/type/confidence when provided.
- `approve_replay`: generate replay candidate and mark as `approved_for_replay`.
- `mark_duplicate`: mark as `duplicate`.

## API

User API:

- `POST /api/memories/{id}/feedback`

Request:

```json
{
  "feedbackType": "wrong_memory",
  "comment": "This memory reverses my preference.",
  "expectedContent": "User prefers concise Chinese summaries.",
  "expectedMemoryType": "preference",
  "expectedCaptureAllowed": true
}
```

Response: `MemoryReviewCaseResponse`.

Admin API:

- `GET /api/admin/memory-review-cases?status=pending_review&limit=50`
- `POST /api/admin/memory-review-cases/{id}/decision`
- `GET /api/admin/memory-review-cases/replay-candidates?limit=200`

Decision request:

```json
{
  "decision": "approve_replay",
  "reviewerComment": "Confirmed from evidence; should be a denial replay case.",
  "correctedContent": null,
  "correctedMemoryType": null,
  "correctedConfidence": null
}
```

## Audit Events

Write `memory_events` rows for:

- `FEEDBACK_REPORTED`, actor `user`.
- `REVIEW_REJECTED`, actor `admin`.
- `REVIEW_ACTIONED`, actor `admin`.
- `REVIEW_APPROVED_FOR_REPLAY`, actor `admin`.
- Memory state changes caused by review decisions use explicit event types:
  - `REVIEW_DISABLED`
  - `REVIEW_DELETED`
  - `REVIEW_UPDATED`

The event `before_json` and `after_json` fields must contain enough detail to reconstruct the change.

## Replay Candidate Rules

Replay candidates are generated only after an admin decision.

Candidate fields:

- `id`: stable `review-{reviewCaseId}` id.
- `source`: `human_review_feedback`.
- `feedbackType`.
- `reviewerDecision`.
- `memoryBefore`.
- `sourceContext`.
- `policySnapshot`.
- `expected`: expected capture behavior and corrected memory fields.
- `manifest`: reviewer, review timestamp, source trace id, and provenance.

No automatic test resource write occurs from production code. The export endpoint gives a reviewed queue for release engineers to promote into the committed replay dataset.

## Security

- User feedback API must use `SecurityUtils.getCurrentUserId()` and `SemanticMemoryRepository.findByIdAndUserId`.
- Admin review APIs must call `AdminAccessGuard.checkAdminAccess()` before service mutation or export.
- A user cannot create feedback for another user's memory.
- Admin actions record reviewer id from `SecurityUtils.getCurrentUserId()`.

## Design Review

Coverage:

- User feedback is structured and durable.
- Admin review is stateful and auditable.
- Replay candidate export is controlled and deliberate.
- Existing memory update/delete controls remain unchanged.
- Frontend productization is intentionally deferred to task 4.

Risk checks:

- Avoids polluting replay data with unreviewed user reports.
- Avoids bypassing user ownership checks.
- Avoids runtime writes to committed test resources.
- Preserves existing memory event ledger while adding a queue table for workflow state.

Open follow-up tasks:

- Task 4 can build a frontend review/explainability UI over these APIs.
- Task 5 can add stronger PII redaction before replay export.
- Task 6 can add metrics for feedback rate, review backlog, and approved replay volume.
