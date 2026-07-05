# Memory Replay Dataset Expansion Design

## Goal

Expand the memory replay and evaluation dataset from a small demonstration set into a governed quality gate for the L3 memory system.

This slice establishes a larger anonymous seed dataset, explicit coverage requirements, and automated dataset-shape validation. It does not claim the seed data is production traffic. Real anonymized samples can later be added through the same case format and coverage categories.

## Scope

In scope:

- Increase `backend/src/test/resources/memory/replay-eval-cases.json` from 25 cases to at least 200 cases.
- Keep every case anonymous, synthetic or sanitized, and free of real user identifiers, real secrets, project names, domains, or customer data.
- Cover more Chinese expressions, ambiguous memory requests, multi-turn-style corrections represented as replay turns, misleading RAG or selected-note references, and complex project-context wording.
- Add dataset quality checks so future edits cannot silently shrink coverage.
- Keep the existing `MemoryReplayEvaluationService` as the policy and candidate evaluation boundary.
- Keep the existing advisor evaluator able to consume the same replay dataset.

Out of scope:

- No real LLM batch evaluation.
- No advisor threshold calibration.
- No prompt version management.
- No human review workflow.
- No frontend UI.
- No production database migration.
- No automatic ingestion of real user conversations.

## Current Baseline

The current replay dataset has 25 cases in:

`backend/src/test/resources/memory/replay-eval-cases.json`

It already covers:

- English and Chinese delete/confirm/cancel operations.
- Explicit and implicit preferences.
- Preference and style corrections.
- Project context with explicit labels.
- Selected-note and RAG reference-only denial.
- One-off style instructions.
- Assistant feedback.
- Sensitive secret denial.
- Forget request denial.

The gap is not only sample count. The current dataset does not encode coverage expectations by category, language, allow/deny balance, or risk type. It can grow accidentally in one narrow direction without improving enterprise readiness.

## Design

Add an explicit replay dataset governance layer inside tests:

1. The replay dataset remains JSON and continues to use `MemoryReplayEvaluationService.MemoryReplayCase`; do not add new JSON fields unless the record and tests are changed in the same TDD slice.
2. Cases use stable IDs with the exact pattern `replay_<language>_<category>_<slug>`.
   - `<language>` is `cn` or `en`.
   - `<category>` is one of the coverage categories in this design.
   - `<slug>` is a short lowercase descriptor with words separated by underscores.
   - Existing legacy IDs should be renamed or replaced during expansion so all final dataset IDs follow this pattern.
   - Because category names contain underscores, tests should classify categories by matching the known category set against the prefix after `replay_<language>_`, not by naively splitting on every underscore.
3. `MemoryReplayEvaluationServiceTest` adds a dedicated dataset-shape test that validates:
   - at least 200 total cases;
   - at least 120 Chinese cases;
   - at least 70 deny cases;
   - at least 70 allow cases;
   - required category prefix minimums from the coverage table below;
   - unique IDs;
   - unique `userMessage` values, because the current advisor oracle test keys expected cases by `userMessage`;
   - nonblank user message and assistant output;
   - every case has a valid expected decision type;
   - every case has an expected policy reason;
   - every non-ambiguous case has at least one expected signal;
   - ambiguous cases may use empty expected signals only when `expectedPolicyReason` is `no_stable_user_memory_signal`;
   - allowed cases declare expected memory type and correction flag;
   - deny cases do not declare expected memory type or correction flag;
   - `cn` cases contain at least one CJK character in the user message.
4. Existing policy evaluation gates remain strict:
   - should-not-remember precision must stay 1.0;
   - should-remember recall must stay 1.0;
   - candidate type accuracy must stay 1.0;
   - correction accuracy must stay 1.0;
   - reason coverage must stay 1.0;
   - signal coverage must stay 1.0.
5. The advisor replay evaluator continues to use the same dataset. This makes the expanded dataset immediately usable later for real advisor batch evaluation and A/B comparison.

## Coverage Categories

The expanded seed dataset must include these minimum category groups:

| Category | Minimum Cases | Expected Side | Purpose |
| --- | ---: | --- | --- |
| `operation` | 15 | deny | Destructive operations, confirmations, cancellations, and short acknowledgements that must not become memory. |
| `explicit_preference` | 15 | allow | Direct "remember" requests in English and Chinese. |
| `implicit_preference` | 18 | allow | Stable preferences without explicit remember wording. |
| `style` | 18 | allow | Stable interaction-style preferences. |
| `correction` | 14 | allow | Preference and style corrections, including Chinese contrastive phrasing. |
| `project_context` | 14 | allow | Explicitly labelled project context and project background. |
| `reference_only` | 15 | deny | Selected-note and current-note requests that mention preferences but must not become user profile memory. |
| `rag_reference` | 15 | deny | RAG output marked as reference-only or operation-target false. |
| `one_off` | 14 | deny | This-turn or this-reply instructions that must not become long-term memory. |
| `assistant_feedback` | 10 | deny | Praise or feedback about the current answer. |
| `sensitive` | 10 | deny | Tokens, credentials, and password-like content using fake examples only. |
| `forget` | 10 | deny | Requests to forget or not store prior information. |
| `ambiguous` | 12 | deny | Unclear or underspecified user statements that should default to deny unless an existing stable signal clearly applies. |
| `multi_turn_correction` | 10 | allow | Replay turns representing a correction after prior preference context, captured as a single turn with explicit correction wording. |
| `complex_project_context` | 10 | allow | Labelled project context with richer but still anonymized project details. |

The first seed expansion should target both breadth and enough scale to expose brittle rules. The initial enterprise-oriented gate is at least 200 cases. After real anonymized samples and human-reviewed failures begin flowing back into the dataset, the target range should grow to 300-500 cases.

The table intentionally sums to 200 cases. Extra cases should first increase Chinese coverage, ambiguous denials, misleading reference-only examples, and correction variants.

## Anonymous Sample Rules

Every replay case must satisfy these rules:

- Use generic users, teams, projects, and products only.
- Do not include real names, company names, production domains, emails, phone numbers, addresses, real tokens, customer data, or internal incident details.
- Secrets may appear only as obviously fake examples such as `sk-test123` or `fake-token-123`.
- Project examples should use generic labels such as "AI note system", "internal knowledge base", or "research workspace".
- If real samples are added later, they must be manually anonymized before entering Git.
- Keep the file UTF-8 encoded. Do not rewrite it through tooling that corrupts Chinese text or escapes it into unreadable mojibake.

## Data Flow

The replay flow stays unchanged:

1. Test loads `replay-eval-cases.json`.
2. `MemoryReplayEvaluationService` creates a `MemoryCapturePolicy.CaptureRequest`.
3. `MemoryCapturePolicy` evaluates allow or deny.
4. `MemoryCandidateExtractor` extracts one candidate for allowed cases.
5. Evaluation compares actual decision, memory type, correction flag, policy reason, and signals against expectations.
6. Dataset-shape tests separately validate size, category, and hygiene requirements.

## Error Handling

Malformed dataset cases should fail tests with direct messages:

- duplicate ID;
- duplicate user message;
- ID that does not match `replay_<language>_<category>_<slug>`;
- missing required field;
- invalid expected decision type;
- missing expected policy reason;
- missing expected signal for a non-ambiguous case;
- ambiguous case with empty expected signals but a policy reason other than `no_stable_user_memory_signal`;
- allowed case without memory type;
- allowed case without correction flag;
- deny case with memory type or correction flag;
- category minimum not met;
- total or Chinese sample minimum not met;
- `cn` case without CJK text in `userMessage`.

These failures should be test failures, not runtime production errors.

## Testing

Use TDD for implementation:

1. Add dataset-shape assertions that fail on the current 25-case dataset.
2. Expand `replay-eval-cases.json` until the shape test passes.
3. Run policy replay tests and keep all existing replay quality metrics at 1.0.
4. Run advisor replay evaluator tests to confirm the expanded dataset still works with the deterministic oracle advisor.
5. Run the focused memory evaluation suite.

If expanded cases expose a real policy gap, stop and classify it before changing production code:

- If the gap is directly inside the memory-capture boundary and blocks this dataset gate, add the failing replay case first, then make the smallest policy or extraction fix under TDD.
- If the gap belongs to advisor tuning, human review, privacy compliance, monitoring, or answer-quality evaluation, document it in the implementation plan as a future fixture, but do not add it to the active passing JSON dataset in this slice.
- Do not weaken expected decisions, policy reasons, or signal expectations to make failures disappear.

Expected focused command:

```powershell
cd D:\ainotetest\backend
mvn "-Dtest=MemoryReplayEvaluationServiceTest,MemoryAdvisorReplayEvaluationServiceTest,MemorySystemEvaluationTest,MemoryCapturePolicyTest,MemoryCandidateExtractorTest,MemorySignalClassifierTest" test
```

## Risks

- Expanding the dataset can expose real policy false negatives or false positives. Those failures should be treated as useful findings, not hidden by weakening assertions.
- If too many cases are crafted only to fit current rules, the dataset will overfit. This design requires ambiguous and misleading cases specifically to pressure the boundary.
- Synthetic data is not a substitute for real anonymized traffic. This slice creates the format and gate; later slices must add real sample harvesting and human review.

## Rollback

This slice changes only tests and test resources. Rollback is a normal Git revert. No database rollback, feature flag, or production data migration is needed.

## Acceptance Criteria

- Replay dataset has at least 200 cases.
- At least 120 cases are Chinese.
- At least 70 cases are deny-side and at least 70 cases are allow-side.
- Dataset contains all coverage categories listed in this design.
- All replay case IDs are unique.
- All replay case IDs follow `replay_<language>_<category>_<slug>`.
- All replay `userMessage` values are unique.
- Every case has expected policy reason.
- Every non-ambiguous case has expected signals.
- Dataset hygiene checks fail on malformed or underspecified cases.
- Existing replay quality metrics remain at 1.0.
- Advisor replay evaluator still accepts the expanded dataset.
- No production runtime behavior changes unless an exposed failing case requires an explicitly reviewed minimal policy fix.

## Self-Review

- Placeholder scan: no unfinished placeholders remain.
- Scope check: this is one backend test-data and test-gate slice.
- Consistency check: the replay service remains the evaluation boundary; no production ingestion is introduced.
- Ambiguity check: synthetic seed data is explicitly not represented as real production traffic.
