# Memory Answer Quality Evaluation Implementation Plan

## Goal

Complete Task 7 with a production-path fix, a paired answer-quality evaluator, a reviewed dataset, a resumable budgeted live run, and a release gate.

## Phase 1: Production Path Corrections

- Make direct read-only streaming assemble context even when no note is selected.
- Add an explicit shared context-priority contract to assembled context.
- Preserve selected-note isolation and active-only memory filtering.
- Add focused regression tests for ordinary memory recall, selected-note priority, RAG conflict policy, and deleted/disabled exclusion.

## Phase 2: Evaluation Core

- Add versioned answer-quality case and provenance records.
- Add deterministic contract scoring and scenario metrics.
- Add a structured model-judge adapter with strict JSON parsing and unavailable classification.
- Add release thresholds and explicit gate failures.

## Phase 3: Formal Batch Execution

- Add token/cost preflight and an explicit live-run opt-in.
- Add per-case timeout, retry, progress JSONL, resume, and final report writing.
- Ensure completed progress is reused and unavailable/error cases can be selectively retried.
- Record model, judge model, prompt/rubric version, dataset version, latency, and timestamps.

## Phase 4: Dataset

- Build at least 120 reviewed cases across all six scenario types.
- Include Chinese majority coverage, English/mixed-language coverage, ambiguous requests, multi-turn corrections, misleading RAG, selected-note conflicts, and complex project context.
- Keep real-human source statements and generated perturbations explicitly distinguishable in provenance.
- Validate uniqueness, category minimums, redaction status, and review status before any live call.

## Phase 5: Verification And Formal Run

- Run focused unit tests and existing memory/context/chat regression tests.
- Run the full backend test suite.
- Run a live budget preflight.
- Run the configured production model over the complete Task 7 dataset.
- Review gate failures, classify model/data/product causes, fix justified product defects, and rerun only failed or unavailable cases.
- Publish the final report and complete a post-implementation audit against the design.

## Acceptance

- Normal read-only streaming can use active long-term memory without requiring a selected note.
- Current user instructions, selected notes, and RAG facts cannot be overridden by stale/profile memory.
- Deleted and disabled memory cannot influence assembled context or evaluated answers.
- A formal run is reproducible, budget bounded, resumable, auditable, and release gated.
- The final Task 7 status is based on persisted evidence, not a manually observed chat example.
