# Memory Answer Quality Task 7 Final Audit

## Decision

Task 7 passes its release gate after production-path repair, model-backed paired evaluation, failure review, and versioned offline rescoring.

Final release evidence:

- report: `backend/target/memory-answer-quality-eval/memory-answer-quality-formal-eval-120-v3-rescored.json`;
- raw model report: `backend/target/memory-answer-quality-eval/memory-answer-quality-formal-eval-120-v3.json`;
- progress ledger: `backend/target/memory-answer-quality-eval/memory-answer-quality-formal-eval-120-v3.progress.jsonl`;
- answer model: `deepseek-chat`;
- prompt: `memory-answer-production-v3`;
- judge rubric: `memory-answer-paired-judge-v3`;
- scorer: `memory-answer-scorer-v2`;
- dataset: `memory-answer-quality-v2-2026-07-10`.

## Final Metrics

| Metric | Result | Gate |
| --- | ---: | ---: |
| Cases | 120 | >= 120 |
| Availability | 100% | >= 99.5% |
| Deterministic contract pass | 100% | >= 95% |
| Judge pass | 100% | >= 95% |
| Treatment win | 100% | >= 80% |
| Harmful regression | 0% | <= 1% |
| Missing-memory honesty | 100% | 100% |
| Preference adherence | 100% | >= 95% |
| Memory benefit | 100% | >= 90% |
| Stale-memory isolation | 100% | 100% |
| Selected-note priority | 100% | 100% |
| RAG/memory conflict resolution | 100% | 100% |
| Deleted/disabled isolation | 100% | 100% |
| Generation p95 | 3863 ms | <= 15000 ms |
| Judge p95 | 2798 ms | <= 15000 ms |
| Timeout/error cases | 0 / 0 | 0 / 0 |

## Findings And Repairs

1. Direct read-only streaming skipped context assembly when no note was selected. It now assembles governed memory context for ordinary read-only questions.
2. Context source priority was implicit. A shared context policy now states current request, selected note, RAG facts, then active long-term memory.
3. The first judge rubric treated active `user_memory` as unsupported. The rubric now distinguishes valid preference/project memory from note-fact authority.
4. No-memory control answers sometimes invented saved preferences. Production chat now returns a deterministic governed response when a memory-recall request has no active long-term memory, and the release gate requires 100% missing-memory honesty.
5. Initial deterministic evidence checks rejected valid Chinese translations and equivalent no-memory wording. Dataset v2 records reviewed semantic alternatives; hard forbidden evidence remains exact and zero tolerance.
6. Progress rows were not version bound. Progress now includes a SHA-256 run fingerprint over model, answer prompt, judge rubric, scorer, and dataset versions.
7. Re-evaluating labels previously implied another paid model run. A compatibility-checked offline rescore path now reuses persisted answers only when model and prompt evidence match.

## Dataset Provenance

The 120-case Task 7 suite contains:

- 8 reviewed anonymized external-real-human preference/style source statements;
- 112 explicitly labelled reviewed adversarial perturbations for project context, stale memory, selected notes, misleading RAG, and deletion/disable isolation.

Generated perturbations are not represented as direct human transcripts. Every case has source type, source reference, scenario tags, review status, reviewer, and approval timestamp.

## Run History

- v1 identified judge/source-priority ambiguity.
- v2 passed the original gate but manual audit found unmeasured missing-memory hallucination.
- v3 added deterministic missing-memory handling and the honesty gate.
- scorer v2 corrected reviewed semantic-equivalence gaps and rescored all persisted v3 answers without additional model calls.

Each live preflight estimated approximately CNY 0.506 under the configured price table. Three live runs were executed; the estimate is not a provider invoice.

## Residual Risk

The formal suite is production-like and model-backed, but only 8 Task 7 cases originate directly from the external human source set. Future review-loop samples should be appended without replacing the adversarial hard-boundary cases. The current release decision is valid for the tested model, prompt, scorer, and dataset versions; any incompatible version change requires a fresh formal run.
