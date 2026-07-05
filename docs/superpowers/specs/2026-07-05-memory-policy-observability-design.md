# Memory Policy Observability Design

## Goal

Make governed memory capture decisions explainable at runtime. When a memory is created, reinforced, or superseded, operators and users should be able to see which policy signals caused the decision.

## Scope

This slice extends the existing L3 memory governance path:

`MemorySignalClassifier -> MemoryCapturePolicy -> MemoryCandidateExtractor -> MemoryWriteService -> MemoryEvent`

It does not add a new table, does not introduce an LLM classifier, and does not change retrieval behavior.

## Design

`MemoryCapturePolicy.CaptureDecision` will carry `matchedSignals` from `MemorySignalClassifier`.

`MemoryCandidateExtractor.MemoryCandidate` will carry:

- `policySignals`
- `decisionType`
- `policyReason`

`MemoryWriteService` will write this policy explanation into:

- `semantic_memories.metadata_json`
- `memory_events.after_json`

The frontend memory event list will parse `afterJson.metadata.policy_signals` and render readable signal chips above the raw snapshot.

## Governance Rules

Existing deny-before-allow ordering remains unchanged.

Denied turns are still not persisted as semantic memories unless they fail unexpectedly, in which case the existing `CAPTURE_FAILED` event remains the observable path.

RAG content, selected-note content, delete actions, confirmations, and one-off instructions remain blocked from long-term user-profile memory.

## Rollback

Rollback is a normal `git revert`. Runtime capture can still be disabled with:

- `app.memory.capture.enabled=false`
- `app.memory.capture.mode=legacy`

No database migration is required.

## Tests

Add tests that prove:

- `CaptureDecision` includes matched policy signals.
- `MemoryCandidateExtractor` propagates policy signals into candidates.
- `MemoryWriteService` writes policy explanation metadata and event snapshots.
- The memory event UI extracts policy signals from event snapshots.
