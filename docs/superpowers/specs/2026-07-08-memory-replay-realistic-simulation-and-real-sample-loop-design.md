# Memory Replay Realistic Simulation And Real Sample Loop Design

## Goal

Upgrade the memory replay evaluation program from a 200-case synthetic seed set to a two-track enterprise dataset program:

1. A 2000-case active replay set, made of the existing 200 `synthetic_seed` cases plus about 1800 new `simulated_realistic` cases generated from diverse multi-agent conversations.
2. A strict real-sample loop where `real_user_anonymized` cases can enter replay only after source provenance, redaction, labeling, and review approval.

This design does not claim agent-generated data is real user data. Agent-generated samples are realistic simulations for coverage pressure. Real user samples require an external source such as production exports, local database exports, or user-provided conversation files.

## Scope

In scope:

- Define multiple persona, scenario, and governance agents for realistic multi-turn data generation.
- Generate about 360-450 multi-turn conversations, each 4-12 turns, and extract about 1800 additional replay/eval cases for a 2000-case active replay gate.
- Cover more Chinese expressions, ambiguous wording, continuous multi-turn corrections, misleading RAG, selected-note conflicts, and complex project context.
- Add provenance metadata so every generated or imported case records its source type and review status.
- Add automated gates that prevent unreviewed, unredacted, or source-less cases from entering the active replay set.
- Add a real-sample intake design for future database/file imports.
- Keep active replay evaluation deterministic and offline in tests.

Out of scope for this phase:

- No claim that simulated cases are real user samples.
- No production data extraction without an explicit data source and user approval.
- No real PII or secrets committed to Git.
- No live LLM calls in unit tests.
- No frontend review UI.
- No advisor threshold calibration or A/B production rollout.

## Source Type Contract

Every case or conversation must carry a source type:

| Source Type | Meaning | Can Claim Real User? | Allowed In Active Replay? |
| --- | --- | --- | --- |
| `synthetic_seed` | Hand-authored or deterministic seed cases. | No | Yes, if reviewed. |
| `simulated_realistic` | Multi-agent generated realistic conversations. | No | Yes, if redacted, labeled, and approved. |
| `real_user_anonymized` | Real user or production-derived sample after anonymization. | Yes | Yes, only with source provenance, redaction report, and approval. |

Tests must fail if a case claims `real_user_anonymized` without a nonblank source reference, redaction report, reviewer identity, and approved review status.

## Agent Architecture

The data program uses three agent layers. They are conceptual roles for generation and review; implementation can be deterministic fixtures, scripted generation, or model-assisted generation outside unit tests.

### User Conversation Agents

These agents create natural user behavior in multi-turn conversations.

| Agent | Personality / Expression Style | Primary Coverage |
| --- | --- | --- |
| `colloquial_cn_user` | Informal Chinese, omissions, topic jumps, weak signals. | Ambiguous Chinese, low-confidence deny. |
| `conflicted_correction_user` | Changes mind, contradicts earlier instructions, revises preferences. | Multi-turn correction, supersede pressure. |
| `project_lead_user` | Clear goals, dense constraints, project phase context. | Complex project context and durable constraints. |
| `engineer_user` | Technical vocabulary, stable tooling and code-style preferences. | Engineering preferences and project rules. |
| `product_ops_user` | Fast-changing operational requests and one-off tasks. | One-off vs durable preference boundaries. |
| `research_knowledge_user` | Quotes notes, papers, search/RAG results, and knowledge snippets. | RAG and selected-note misattribution. |
| `emotional_feedback_user` | Praise, frustration, complaints, strong tone. | Assistant feedback should not become memory. |
| `privacy_risk_user` | Accidentally provides fake credentials, tokens, personal details. | Sensitive information denial. |
| `operation_user` | Delete, confirm, cancel, rename, move, clear, update. | Operations and confirmations should not become memory. |
| `mixed_language_user` | Chinese-English mixed text and domain terms. | Multilingual and code-switched expressions. |
| `novice_user` | Unclear intent, vague phrasing, asks for help discovering preference. | Ambiguous memory signals. |
| `power_user` | Explicitly says remember, forget, replace, disable, override. | Explicit memory, forget, correction. |

### Scenario And Interference Agents

These agents add realistic context that can mislead memory capture.

| Agent | Role |
| --- | --- |
| `rag_context_agent` | Inserts reference-only RAG snippets, external search results, and quoted material. |
| `selected_note_agent` | Inserts current-note or selected-note text that may mention preferences unrelated to the user. |
| `project_context_agent` | Provides anonymized project background, team rules, milestones, and constraints. |
| `conflict_agent` | Creates conflicts between long-term user preference, current note, RAG, and latest user correction. |
| `noise_agent` | Adds typos, short acknowledgements, repetitions, ellipsis, incomplete clauses, and irrelevant turns. |

### Data Governance Agents

These agents keep generated data usable and auditable.

| Agent | Role |
| --- | --- |
| `pii_redaction_agent` | Detects and replaces names, emails, phones, addresses, domains, tokens, and secret-like strings. |
| `labeling_agent` | Assigns expected capture decision, decision type, policy reason, signals, memory type, and correction flag. |
| `consistency_reviewer` | Checks labels against the conversation and expected policy behavior. |
| `diversity_auditor` | Detects over-templating, low vocabulary diversity, and category imbalance. |
| `replay_extractor` | Extracts replay cases from multi-turn conversations while preserving turn provenance. |
| `provenance_auditor` | Ensures simulated data is marked `simulated_realistic` and never as real user data. |
| `regression_curator` | Promotes approved failure cases into replay and records why they were added. |

## Scenario Matrix

The 2000-case active target must be drawn from multi-turn conversations across these scenario families:

| Scenario Family | Minimum Replay Cases | Notes |
| --- | ---: | --- |
| Chinese stable preference expressions | 240 | Include formal, colloquial, terse, indirect, and mixed-language variants. |
| Ambiguous or weak memory signals | 200 | Default deny unless a stable memory signal is explicit enough. |
| Continuous multi-turn correction | 240 | Include "not that, this", "change back", "from now on", and contradictory turns. |
| Misleading RAG/reference-only context | 240 | RAG content must not become user profile memory. |
| Selected/current note conflicts | 160 | Note content must not override user memory unless user states it as preference. |
| Complex project context | 240 | Multi-constraint project backgrounds, still generic and anonymized. |
| Operation/confirmation/cancellation | 160 | Delete, clear, move, rename, confirm, cancel, ok, yes/no. |
| One-off instruction vs durable preference | 160 | This reply, this time, this task only. |
| Assistant feedback | 100 | Praise, complaint, "this answer is good/bad". |
| Sensitive/PII-like content | 100 | Only fake examples; must deny capture. |
| Forget/delete memory control | 80 | Requests to forget, stop remembering, replace old memory. |
| Multilingual/code-switched expressions | 120 | Chinese-English mixed cases. |

These minimums intentionally sum above 2000 because one case may have multiple scenario tags. Category gates remain separate from scenario tags.

## Category Distribution Target

The 2000-case active gate should preserve the 200-case seed distribution, scaled by 10. This gives broad coverage without shifting the policy boundary toward only allow or only deny cases.

| Category | Minimum Active Cases |
| --- | ---: |
| `operation` | 150 |
| `explicit_preference` | 150 |
| `implicit_preference` | 180 |
| `style` | 180 |
| `correction` | 140 |
| `project_context` | 140 |
| `reference_only` | 150 |
| `rag_reference` | 150 |
| `one_off` | 140 |
| `assistant_feedback` | 100 |
| `sensitive` | 100 |
| `forget` | 100 |
| `ambiguous` | 120 |
| `multi_turn_correction` | 100 |
| `complex_project_context` | 100 |

The category minimums sum to 2000. The implementation may exceed 2000 only if the shape gate remains balanced and reviewable. The recommended target is exactly 2000 to avoid low-value fixture bloat.

## Conversation Record Design

Raw generated or imported conversations should not be the same artifact as active replay cases.

Proposed resource layout:

```text
backend/src/test/resources/memory/replay-eval-cases.json
backend/src/test/resources/memory/replay/conversations/
backend/src/test/resources/memory/replay/manifests/
backend/src/test/resources/memory/replay/redaction-reports/
backend/src/test/resources/memory/replay/review-reports/
```

Conversation records should include:

- `conversationId`
- `sourceType`
- `sourceReference`
- `personaAgent`
- `scenarioAgents`
- `scenarioTags`
- `languageTags`
- `turns`
- `redactionReportId`
- `reviewStatus`
- `reviewer`
- `approvedAt`
- `extractedCaseIds`

Replay cases should remain compatible with `MemoryReplayEvaluationService.MemoryReplayCase`, but the second phase may add a sidecar manifest keyed by case ID so active evaluator records do not need to carry all provenance fields.

## Real Sample Intake Loop

Real user samples can enter only through this loop:

1. Intake source is declared:
   - local database export from `user_memories`;
   - production export;
   - user-provided JSON/CSV/log file.
2. Source rows are copied into an intake area outside active replay.
3. PII and secret redaction runs before review.
4. Redaction report records every replacement category, not the original sensitive value.
5. Labeling proposes expected memory decision and signals.
6. Consistency review approves or rejects labels.
7. Diversity audit checks whether the sample adds coverage.
8. Approved cases are extracted into replay JSON or generated replay fragments.
9. Manifest links every case ID to source type, redaction report, reviewer, and source reference.
10. Tests fail if any `real_user_anonymized` case lacks provenance or approval.

No raw real sample should be committed to Git. Only redacted approved artifacts may be committed.

## Data Flow

### Simulated Realistic Flow

1. Persona agent starts or continues a multi-turn conversation.
2. Scenario/interference agents inject RAG, selected note, project context, conflict, or noise.
3. Assistant response is simulated or captured from a controlled deterministic response.
4. Governance agents redact, label, review, and audit diversity.
5. Replay extractor emits one or more replay cases.
6. Manifest records `sourceType=simulated_realistic`.
7. Active replay tests evaluate all approved cases.

### Real User Anonymized Flow

1. Importer reads source data with explicit approval.
2. Redaction removes or replaces identifiers and secrets.
3. Reviewer verifies anonymization and labels.
4. Approved cases enter the same replay extraction path.
5. Manifest records `sourceType=real_user_anonymized`.

## Quality Gates

Automated tests should enforce:

- active replay total cases >= 2000;
- Chinese cases >= 1300;
- allow cases >= 700;
- deny cases >= 700;
- all existing 15 categories still covered;
- each category meets the 2000-case category distribution target;
- scenario tags cover the scenario matrix;
- each active case has unique ID and unique `userMessage`;
- every active case has source type in the manifest;
- `simulated_realistic` cases cannot claim real-user provenance;
- `real_user_anonymized` cases require source reference, redaction report, reviewer, approved status, and approval timestamp;
- no replacement character `\uFFFD`;
- no obvious PII patterns, real emails, real phone numbers, real domains, or scanner-risk secrets;
- no unapproved case appears in active replay;
- replay policy metrics remain strict at 1.0 unless a future reviewed policy change explicitly updates expectations.

## Data Diversity Requirements

The dataset should avoid 2000 near-duplicates. Diversity checks should include:

- category and scenario distribution;
- persona distribution;
- language distribution;
- sentence-length distribution;
- duplicate `userMessage` detection;
- near-duplicate text detection by normalized token overlap;
- Chinese punctuation and wording variety;
- mixed Chinese-English coverage;
- multi-turn extraction coverage, not only single-turn hand-authored cases.

## Error Handling

Dataset validation should fail with direct messages for:

- missing manifest entry for active case;
- source type mismatch;
- unapproved active case;
- `real_user_anonymized` case without provenance;
- raw PII or secret-like value in committed resources;
- missing redaction report;
- missing reviewer;
- missing scenario tags;
- category or scenario coverage below minimum;
- duplicate ID or duplicate `userMessage`;
- malformed JSON or manifest references.

## Testing

Use TDD:

1. Add failing manifest/provenance gates against the current 200-case dataset.
2. Add sidecar manifest for existing `synthetic_seed` cases.
3. Add realistic conversation and extraction fixtures.
4. Raise active replay minimum to 2000 only after extraction/generation artifacts are in place.
5. Add hygiene tests for PII, source provenance, review status, and diversity.
6. Keep replay/advisor tests deterministic and offline.
7. Run focused memory tests and full backend tests before completion.

Expected focused commands:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryReplayEvaluationServiceTest,MemoryAdvisorReplayEvaluationServiceTest" test
mvn "-Dtest=MemoryReplayEvaluationServiceTest,MemoryAdvisorReplayEvaluationServiceTest,MemorySystemEvaluationTest,MemoryCapturePolicyTest,MemoryCandidateExtractorTest,MemorySignalClassifierTest" test
```

## Acceptance Criteria

- The design uses `simulated_realistic` for agent-generated data and never labels it as real user data.
- The implementation plan targets 2000 active replay cases.
- The active dataset includes broad Chinese, ambiguous, multi-turn correction, misleading RAG, selected-note conflict, and complex project-context coverage.
- Every active case has manifest provenance.
- Any future `real_user_anonymized` case requires redaction and approval metadata.
- No raw real user data, real PII, or real secrets are committed.
- Replay evaluation remains deterministic and offline.
- Full backend tests pass before the work is called complete.

## Rollback

This phase should change only tests, test resources, scripts or services used for test-data governance, and documentation. Rollback is a normal Git revert. If a later phase adds production import endpoints or database migrations, that phase must define its own rollback.

## Self-Review

- Placeholder scan: no TBD or TODO markers remain.
- Scope check: this spec covers dataset generation, provenance, and review gates; it does not implement frontend review UI or production extraction.
- Terminology check: `simulated_realistic` is distinct from `real_user_anonymized`.
- Ambiguity check: real user status requires external source provenance and cannot be inferred from agent-generated conversations.
