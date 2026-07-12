# Memory Explainability UI Design

## Goal

Build task 4: a productized frontend for memory explainability and human review operations.

This task turns the existing backend governance, task 3 feedback loop, and memory audit data into usable product surfaces:

- Users can understand why a memory exists and report wrong memories.
- Admins can review feedback cases, apply decisions, and export approved replay candidates.

## Approved Scope

In scope:

- User-facing memory explanation in the existing AI memory settings page.
- User-facing "this memory is wrong" feedback flow.
- Admin-facing review queue in the same memory settings area.
- Admin review actions backed by task 3 APIs.
- Replay candidate export button for approved cases.
- Minimal failure-sample visibility through reviewed cases and approved replay candidates.
- Small backend response-model addition so frontend explanations do not rely on parsing event snapshots.
- Frontend tests for explanation fields, feedback submission, admin guard-friendly review API calls, review decisions, and replay export.

Out of scope:

- Production metrics dashboard.
- Operational metrics such as write rate, reject rate, advisor failure rate, deletion rate, p95 latency, or context injection count.
- Stronger PII detection and redaction policy.
- Answer quality evaluation.
- New standalone enterprise admin console.

## Current State

Existing frontend:

- `frontend/src/components/MemoryControlPanel.tsx` already lists memories, supports disable/delete/export, shows evidence excerpt, trace id, context preview, and recent memory events.
- `frontend/tests/memory-control-panel.behavior.test.tsx` already covers list/search, disable/delete, context preview, and event ledger.
- `frontend/src/api.ts` already has memory list/update/delete/export/event APIs.

Existing backend:

- `MemoryResponse` exposes content, type, status, source trace id, source message ids, evidence excerpt, access count, supersedes id, timestamps.
- `MemoryEventResponse` exposes audit ledger rows.
- Task 3 added user feedback and admin review APIs.
- `SemanticMemory.metadataJson` contains explanation data such as `capture_reason`, `policy_reason`, `decision_type`, `candidate_confidence`, `policy_signals`, and `policy_source`.

Gap:

- `MemoryResponse` does not currently return `metadataJson`, so the frontend must infer explanation from event snapshots. That is brittle and not product quality.
- User feedback exists in backend but is not reachable from the UI.
- Admin review operations exist in backend but have no frontend surface.
- The existing page mixes context preview, event ledger, and memory list without a clear distinction between user explanation and admin review.

## Chosen Approach

Use a dual-workspace design inside the existing AI memory settings page:

- `My Memories`: user-facing explanations and feedback.
- `Review Queue`: admin review queue and decisions.

`My Memories` must be the default workspace. The admin review queue must not request admin APIs until the user explicitly selects `Review Queue`.

This keeps task 4 scoped to memory explainability and review. It avoids a large standalone governance console, which would overlap with task 6 production observability.

## User Workspace: My Memories

Each memory row should show a compact summary:

- Content.
- Memory type/category.
- Status.
- Confidence.
- Evidence excerpt.
- Updated time.
- Access count.

Each memory row should expose an explanation details section:

- Why it was captured: `metadata.capture_reason`.
- Policy reason: `metadata.policy_reason`.
- Decision type: `metadata.decision_type`.
- Candidate confidence: `metadata.candidate_confidence`.
- Policy signals: `metadata.policy_signals`.
- Source: `metadata.policy_source`.
- Advisor involvement:
  - show "advisor" when `metadata.policy_source` contains `advisor` or any `metadata.policy_signals` value starts with `advisor_`;
  - show "rules" when signals exist but no advisor marker exists;
  - show "unknown" when metadata is absent or unparsable.
- Source trace id.
- Source message ids.
- Source tool call id.
- Supersedes id, when present.
- Per-memory audit events loaded with `GET /api/memories/events?memoryId={id}&limit=25` when a row is expanded.

The source section must display trace and source message references as references, not as a guaranteed full conversation transcript. Current backend data may contain message hashes rather than raw message ids.

The UI should productize only known metadata keys:

- `capture_reason`
- `policy_reason`
- `decision_type`
- `candidate_confidence`
- `policy_signals`
- `policy_source`

The full raw `metadataJson` may be shown only inside an expandable diagnostic section for debugging and must not dominate the memory row.

For legacy memories with missing or invalid `metadataJson`, the row must still render. The explanation should show an explicit "no structured explanation available" state, keep evidence/trace/event references visible, and mark advisor involvement as `unknown`.

The row should also expose:

- Disable/enable.
- Delete.
- Report wrong memory.

The report flow should collect:

- Feedback type:
  - wrong memory
  - should not remember
  - type wrong
  - outdated
  - missing context
  - other
- User comment.
- Optional expected content.
- Optional expected memory type.
- Optional expected capture allowed.

Display labels may be human-readable, but submitted API values must be exactly:

- `wrong_memory`
- `should_not_remember`
- `type_wrong`
- `outdated`
- `missing_context`
- `other`

On submit, call `POST /api/memories/{id}/feedback`.

## Admin Workspace: Review Queue

The admin workspace should use the task 3 admin APIs:

- `GET /api/admin/memory-review-cases?status={status}&limit={limit}`
- `POST /api/admin/memory-review-cases/{id}/decision`
- `GET /api/admin/memory-review-cases/replay-candidates`

Review queue display:

- Status filter with at least `pending_review`, `rejected`, `actioned`, `approved_for_replay`, and `duplicate`.
- Limit control capped by the backend.
- Review case id.
- Memory id.
- Feedback type.
- User comment.
- Expected content/type/capture decision.
- Review status.
- Created/reviewed timestamps.
- Memory before snapshot.
- Source context snapshot.
- Policy snapshot.
- Replay case id when approved.

Decision controls:

- Reject feedback: submit `reject_feedback`.
- Disable memory: submit `disable_memory`.
- Delete memory: submit `delete_memory`.
- Update memory: submit `update_memory`.
- Approve replay: submit `approve_replay`.
- Mark duplicate: submit `mark_duplicate`.

For update decisions, allow:

- Corrected content.
- Corrected memory type.
- Corrected confidence.
- Reviewer comment.

For non-update decisions, require only reviewer comment when useful.

Replay export:

- Show approved replay candidates.
- Provide a download JSON button.
- Treat approved replay candidates as the task 4 failure-sample view.
- Do not add aggregate evaluation charts here; those belong to task 6 production observability.

## Backend Contract Changes

Add `metadataJson` to:

- `MemoryResponse`.
- `MemoryControlService.toResponse`.

No new database migration is required because `semantic_memories.metadata_json` already exists.

No backend business logic changes are required beyond returning the existing field.

## Frontend API Changes

Extend `frontend/src/api.ts`:

- Add `metadataJson` to `MemoryRecord`.
- Add `MemoryFeedbackPayload`.
- Add `MemoryReviewCaseRecord`.
- Add `MemoryReviewDecisionPayload`.
- Add `MemoryReplayCandidateExportResponse`.
- Add `submitMemoryFeedback(id, payload)`.
- Add `getMemoryReviewCases(params)`.
- Add `decideMemoryReviewCase(id, payload)`.
- Add `exportMemoryReplayCandidates(limit)`.

## Component Design

Keep `MemoryControlPanel` as the entry point, but split internally:

- `MemoryControlPanel`: owns tab/workspace selection, shared loading state, and high-level layout.
- `MemoryExplanationList`: renders user memory rows and explanation details.
- `MemoryFeedbackDialog`: handles wrong-memory report submission.
- `MemoryReviewQueue`: renders admin review cases and decision controls.
- `MemoryReplayExportPanel`: renders approved replay candidate export.

If implementation risk is lower, these can start as internal components in the same file and be extracted only if the file becomes hard to test. The target design should avoid adding a large all-purpose component.

## Error Handling

User workspace:

- Memory list load failure shows inline error and toast.
- Feedback submit failure keeps dialog open and shows failure toast.
- Successful feedback closes dialog and shows success toast.

Admin workspace:

- Admin APIs must be loaded lazily only when the `Review Queue` workspace is selected.
- Review queue load failure shows inline error and toast.
- Decision failure keeps the case visible and shows failure toast.
- Successful decision refreshes review queue and events.
- Replay export failure shows failure toast.

Authorization:

- If admin APIs return 403, show a quiet "admin access required" state in the admin workspace instead of breaking the user memory page.
- A 403 from the admin API must not create a global failure toast on initial page load, because ordinary users are allowed to use `My Memories`.

JSON parsing:

- `metadataJson`, `memoryBeforeJson`, `sourceContextJson`, `policySnapshotJson`, `replayCaseJson`, and `manifestJson` must be parsed with safe helpers.
- Invalid JSON must render as raw text inside an expandable diagnostic block, not crash the settings page.
- Missing metadata must use the same no-structured-explanation fallback as invalid metadata.

## Testing

Backend tests:

- `MemoryControlServiceTest` verifies `metadataJson` is mapped into `MemoryResponse`.
- `MemoryControllerTest` verifies list/export responses can include metadata.

Frontend tests:

- Existing `memory-control-panel.behavior.test.tsx` should be extended or split.
- User explanation test verifies metadata-driven explanation fields render.
- Legacy explanation test verifies missing or invalid `metadataJson` does not crash and renders the no-structured-explanation fallback.
- Feedback test verifies `submitMemoryFeedback` is called with selected type/comment/expected fields.
- Feedback test must assert submitted enum values use backend values such as `wrong_memory`, not human-readable labels.
- Admin review queue test verifies `getMemoryReviewCases` results render.
- Admin decision test verifies `decideMemoryReviewCase` is called with decision and corrected fields.
- Admin decision test must assert submitted decision values use backend values such as `approve_replay`.
- Replay export test verifies approved candidates can be downloaded.
- Forbidden admin test verifies user memory page remains usable when admin review API returns 403.
- Per-memory event test verifies expanding a memory calls `getMemoryEvents({ memoryId, limit: 25 })`.

## Design Review

Scope check:

- The design covers task 4 only: explainability and review frontend.
- Task 6 metrics are intentionally excluded.
- Failure samples are included only as reviewed cases and approved replay candidates; aggregate metrics remain excluded.
- Task 5 privacy/compliance changes are intentionally excluded except for displaying already available metadata.

Backend compatibility:

- Uses existing database column `metadata_json`.
- Uses task 3 feedback/review APIs.
- Does not require new migration.

Frontend compatibility:

- Builds on the existing settings memory panel and tests.
- Keeps layout dense and operational rather than creating a marketing-style page.
- Uses current design tokens and existing icon/button patterns.

Ambiguity resolved:

- Terminal selection overrides browser click history; the approved layout is B.
- Admin review is included, but production metrics are excluded.
- Event snapshots remain audit evidence, not the primary explanation source.
- Backend enum values are canonical; frontend labels are only presentation text.
