# Test Gap Closure Report

Date: 2026-07-02
Branch: fix/audit-remediation

## Scope

This report records the real verification performed after the test-gap audit and follow-up remediation pass. The previous false-green risks have been converted into executable release gates.

## Verified Gates

| Gate | Command / Evidence | Result |
|---|---|---|
| Backend full verification | `cd backend && mvn -q clean verify` with `AINOTE_IT_TEMP_DB=true` against local pgvector | Passed with 0 skipped tests |
| Backend test report XML | `backend/target/surefire-reports` + `backend/target/failsafe-reports` | 771 tests, 0 failures, 0 errors, 0 skipped |
| Backend JaCoCo agent coverage | `backend/target/site/jacoco/jacoco.xml` | 83.91% instruction coverage |
| Backend JaCoCo planning coverage | `backend/target/site/jacoco/jacoco.xml` | 80.28% instruction coverage |
| Frontend unit tests | `cd frontend && npm run test:coverage` | 21 files / 58 tests passed |
| Frontend coverage | `cd frontend && npm run test:coverage` | statements 48.45%, branches 38.62%, functions 51%, lines 50.80% |
| Frontend build | `cd frontend && npm run build` | Passed |
| Mocked Playwright journey | `cd frontend && npm run test:e2e -- e2e/ainote.spec.ts` | 6 tests passed |
| Real full-stack Playwright | `AINOTE_E2E_FULLSTACK=true AINOTE_BACKEND_URL=http://127.0.0.1:8081 npm run test:e2e -- e2e/fullstack.spec.ts` | 2 tests passed |
| Real AI smoke | `powershell -ExecutionPolicy Bypass -File scripts\verify-real-ai.ps1` | Passed; embedding dimension 1024 |
| k6 local core profile | `PERF_PROFILE=local_core k6 run perf\ainote-load.js` through `scripts/verify-release.ps1` | Passed with 10,000 seeded notes |
| k6 embedding direct profile | `PERF_PROFILE=embedding_direct k6 run perf\ainote-load.js` through `scripts/verify-release.ps1` | Passed against local TEI |
| k6 scheduler soak profile | `PERF_PROFILE=scheduler_soak DURATION=30m k6 run perf\ainote-load.js` through `scripts/verify-release.ps1 -RunSchedulerSoak` | Passed |
| k6 SSE 100 profile | `PERF_PROFILE=sse_100 SSE_VUS=100 k6 run perf\ainote-load.js` through `scripts/verify-release.ps1` | Passed; token/complete events and thread delta asserted |
| k6 admitted-agent SSE 100 profile | `CHAT_DIRECT_STREAM_ENABLED=false`, `SSE_REQUIRE_AGENT_ADMITTED=true`, 100 user tokens, `SSE_VUS=100`, `DURATION=30s` | Passed; 1323/1323 admitted, no direct stream, no busy, complete p95 1708.41 ms |
| k6 LLM profile | `PERF_PROFILE=llm k6 run perf\ainote-load.js` through `scripts/verify-release.ps1` | Passed |
| k6 Agent saturation profile | `PERF_PROFILE=agent_saturation AGENT_SATURATION_VUS=16 k6 run perf\ainote-load.js` through `scripts/verify-release.ps1` | Passed; 503 overload path asserted |
| k6 Business API profile | `PERF_PROFILE=business_api k6 run perf\ainote-load.js` through `scripts/verify-release.ps1` | Passed across authenticated business endpoint families |
| 10k local RAG vector gate | `RagVectorIndexPerformanceIT` inside backend verify | Passed; p95 24 ms; HNSW index plan asserted |
| 10k scheduler query gate | `TaskScheduleRunnerPerformanceIT` inside backend verify | Passed; p95 24 ms; next-run index plan asserted |
| Release wrapper | `powershell -ExecutionPolicy Bypass -File scripts\verify-release.ps1 -RunSchedulerSoak` | Passed and printed `release-verification=passed` |

## Added or Strengthened Coverage

| Area | Status |
|---|---|
| Link preview controller contract | Added `LinkPreviewControllerTest` |
| AI stream send-level behavior | Added direct SSE error event and completion/client-error cancellation tests |
| Invalid JWT behavior | Fixed and covered unauthorized response handling |
| Real full-stack E2E | Added non-mocked Playwright suite against Spring Boot, JWT, DB persistence |
| Tiptap real rendering | Added real editor smoke tests with minimal Tiptap mocking |
| App shell real rendering | Added composed App shell behavior coverage |
| Chat/card frontend depth | Added behavior tests for chat input, AI cards, pending actions, workflow card states |
| Real AI verification | Added repeatable DeepSeek + TEI smoke script |
| Docker false-green prevention | Testcontainers-backed ITs now use a release-wrapper temp pgvector database and skipped Maven tests are fatal |
| 10k local RAG verification | Added `RagVectorIndexPerformanceIT` to seed 10,000 vectors, assert index usage, and enforce p95 |
| Performance verification | Split local core, direct embedding, scheduler soak, SSE 100, LLM, Agent saturation, and Business API k6 profiles so local SLOs, external provider latency, streaming concurrency, overload behavior, and business endpoint latency are measured separately |
| Release verification | Added wrapper enforcement for Docker, backend, skipped tests, frontend, Playwright, real AI, k6, business endpoints, and coverage gates |
| Deep repository/link coverage | Added RepositoryIT coverage for NoteConcept, SemanticMemory pgvector update/search, EpisodicMemory, schedule reminder notifications; added wikilink rename/broken-link regression |

## Performance Results

Final `k6` local core profile in the release wrapper:

| Metric | Result | Threshold |
|---|---:|---:|
| checks | passed | all pass |
| `http_req_failed` | 0.00% | < 1% |
| `notes_pagination_latency` p95 | 130.47 ms | < 300 ms |
| `scheduler_latency` p95 | 48.75 ms | < 1000 ms |

Final `k6` embedding direct profile in the release wrapper:

| Metric | Result | Threshold |
|---|---:|---:|
| `http_req_failed` | 0.00% | < 1% |
| `embedding_direct_latency` p95 | 1549.36 ms | < 4500 ms |

Final `k6` scheduler soak profile in the release wrapper:

| Metric | Result | Threshold |
|---|---:|---:|
| duration | 30 minutes | 30 minutes |
| checks | 3518 / 3518 | all pass |
| `http_req_failed` | 0.00% | < 1% |
| `scheduler_latency` p95 | 33.37 ms | < 1000 ms |

Final `k6` SSE 100 profile in the release wrapper:

| Metric | Result | Threshold |
|---|---:|---:|
| checks | 8838 / 8841 | pass threshold |
| `http_req_failed` | 0.03% | < 5% |
| `sse_accepted` | 99.96% | > 99% |
| `sse_token_received` | 99.96% | > 99% |
| `sse_complete_received` | 99.96% | > 99% |
| `sse_first_token_latency` p95 | 101.77 ms | < 2000 ms |
| `sse_complete_latency` p95 | 103.67 ms | < 5000 ms |
| `jvm.threads.live` prewarmed delta | 0 | <= 20 |
| non-blocking warning | 1 EOF out of 2,947 requests | within threshold |

Final strict admitted-agent SSE 100 profile:

| Metric | Result | Threshold |
|---|---:|---:|
| VUs / duration | 100 / 30s | 100 / 30s |
| checks | 10584 / 10584 | all pass |
| `http_req_failed` | 0.00% | < 5% |
| `sse_accepted` | 100.00% | > 99% |
| `sse_agent_admitted` | 100.00% (1323 / 1323) | > 99% |
| `sse_agent_busy` | 0.00% | 0 expected |
| `sse_direct_stream` | 0.00% | 0 expected |
| `sse_complete_latency` p95 | 1708.41 ms | < 5000 ms |
| `sse_first_token_latency` p95 | 246.14 ms | < 2000 ms |

Final `k6` LLM profile in the release wrapper:

| Metric | Result |
|---|---:|
| checks | 726 / 726 |
| `http_req_failed` | 0.00% |
| `sse_accepted` | 100% |
| `agent_complete_latency` p95 | 3274.43 ms |
| `agent_route_latency` p95 | 3814.55 ms |
| `rag_cold_with_rewrite_latency` p95 | 10832.69 ms |
| `sse_first_token_latency` p95 | 213.13 ms |
| `sse_complete_latency` p95 | 215.16 ms |

Final `k6` Agent saturation profile in the release wrapper:

| Metric | Result | Threshold |
|---|---:|---:|
| checks | 269 / 269 | all pass |
| `http_req_failed` | 0.00% | < 1% |
| `agent_saturation_rejected` | 69.51% | > 0 |
| `agent_saturation_reject_latency` p95 | 39.79 ms | < 1000 ms |

Final `k6` Business API profile in the release wrapper:

| Metric | Result | Threshold |
|---|---:|---:|
| checks | 498 / 498 | all pass |
| `http_req_failed` | 0.00% | < 1% |
| `business_api_success` | 100% | > 99% |
| `business_api_latency` p95 | 351.33 ms | < 1000 ms |
| `business_notes_latency` p95 | 572.59 ms | < 1000 ms |
| `business_schedules_latency` p95 | 691.01 ms | < 1000 ms |
| `business_batch_latency` p95 | 439.11 ms | < 1500 ms |
| `business_graph_latency` p95 | 77.48 ms | < 2000 ms |

Final 10k local RAG vector gate:

| Metric | Result | Threshold |
|---|---:|---:|
| Seeded chunks | 10,000 | 10,000 |
| Vector dimensions | 1024 | 1024 |
| Vector plan | `idx_l4j_embeddings_vec` asserted | required |
| p95 | 24 ms | < 800 ms |

Final 10k scheduler query gate:

| Metric | Result | Threshold |
|---|---:|---:|
| Seeded schedules | 10,000 | 10,000 |
| Due scans | 30 measured scans | 30 |
| Query plan | `idx_task_schedules_next_run` asserted | required |
| p95 | 24 ms | < 1000 ms |

## Closed Strict Gaps

| Gap | Closure |
|---|---|
| Docker/Testcontainers ITs were skipped | Release wrapper now starts a temporary pgvector database, passes it to the integration tests, and fails if any Maven XML report contains skipped tests |
| `SchemaValidationIT` not truly executed | `SchemaValidationIT`, `FlywayReplayIT`, `FolderDeleteCascadeIT`, `RepositoryIT`, `NoteOptimisticLockTest`, and `AiNoteApplicationTest` all executed with 0 skipped tests in the full backend verify |
| True 100 admitted-agent + DeepSeek capacity was not proven | Strict admitted-agent k6 profile passed with direct-stream disabled, 100 distinct user tokens, 1323/1323 admitted completions, no `AGENT_BUSY`, and complete p95 1708.41 ms |
| Frontend coverage gate was advisory | `frontend/vitest.config.ts` thresholds and CI `Vitest coverage gate` now enforce coverage via `npm run test:coverage` |
| Repository CRUD and cross-link depth gaps | `RepositoryIT` now covers NoteConcept/SemanticMemory/EpisodicMemory and notification read-state flow; `KnowledgeGraphServiceWikiLinkTest` covers rename/broken-link behavior |
| `TC-PERF-03` 10k local RAG gate previously missing | `RagVectorIndexPerformanceIT` now seeds 10,000 embeddings, runs vector retrieval, asserts index-plan evidence, and enforces p95 < 800 ms |
| `TC-PERF-01` SSE 100 concurrency was indirect | `sse_100` k6 profile now asserts 100 concurrent streams, token events, complete events, first-token latency, complete latency, and prewarmed JVM thread delta |
| `TC-PERF-02` Agent saturation was not a real k6 gate | `agent_saturation` k6 profile now sends 16 VUs to `/api/ai/smart-chat`, treats 503 as expected overload control, and enforces rejection latency |
| `TC-PERF-04` 10k notes pagination was not data-verified | `scripts/seed-perf-notes.ps1` seeds 10,000 notes and the local core k6 profile asserts `totalElements >= 10000` |
| `TC-PERF-05` scheduler long-run was not executed | `scheduler_soak` now ran for 30 minutes and `TaskScheduleRunnerPerformanceIT` covers 10k-query shape with index evidence |
| Release wrapper false green | `scripts/verify-release.ps1` now fails on native command nonzero exits and on any skipped Maven test |

## Safety Checks

| Check | Result |
|---|---|
| Real API key committed to repository | Not found by secret scan |
| Scanner-detectable complete `sk-...` literals | Not found; guardrail tests construct fake redaction samples at runtime |
| `AUDIT_REPORT.md` ignored | `.gitignore` contains it |
| `FIX_PLAN.md` ignored | `.gitignore` contains it |
| `VERIFICATION_REPORT.md` ignored | `.gitignore` contains it |
| `.codex-run/` ignored | `.gitignore` contains it |
| `frontend/coverage/` ignored | `.gitignore` contains it |
| Test database | Verification used a temporary pgvector database created by the release wrapper, not a production database |
| Temporary pgvector cleanup | Release wrapper stops and removes the temp `ainote-it-pg-*` container in `finally` |

## Release Decision

Code and strict release verification gates now pass locally.

No known Section 11 strict test gap remains from this audit pass.
