# AI Note Performance Gates

## Profiles

`PERF_PROFILE=local` is the manual combined local profile. It verifies notes pagination, scheduler API paths, and direct local TEI embedding in one k6 run.

`PERF_PROFILE=local_core` is the release-wrapper local application gate. It verifies local backend note pagination and scheduler API latency without invoking embedding, so business CRUD and core local SLOs are measured without AI-provider noise.

`PERF_PROFILE=embedding_direct` is the release-wrapper embedding gate. It calls the local OpenAI-compatible TEI endpoint directly and validates embedding latency separately. The raw k6 script defaults `EMBEDDING_P95_MS` to 2500ms; `scripts\verify-release.ps1` defaults its CPU TEI release gate to 4500ms unless `-EmbeddingP95Ms` is supplied.

`PERF_PROFILE=llm` verifies real DeepSeek route/chat/SSE paths and reports provider latency separately. It gates cold RAG/search rewrite p95 with `RAG_COLD_REWRITE_P95_MS` (raw k6 default 8000ms) while avoiding local 800ms RAG thresholds for full external LLM responses.

`PERF_PROFILE=sse_100` verifies 100 concurrent SSE streams. It defaults to the GET/EventSource-compatible endpoint and requires accepted streams, heartbeat events, token events, complete events, SSE `retry:` fields, SSE `id:` fields, first-token p95 < 2s, complete p95 < 5s, and the release wrapper checks `jvm.threads.live` before and after the run. Safe read-only prompts may use the optimized `DIRECT_STREAM` path; set `SSE_EXPECT_DIRECT_STREAM=true` when that is the intended gate. With one `AUTH_TOKEN`, this is mainly an SSE lifecycle/backpressure gate because the agent guard allows one active agent request per user. For multi-user admitted-agent SSE, pass comma-separated `AUTH_TOKENS` or `AUTH_TOKENS_FILE`, set `CHAT_DIRECT_STREAM_ENABLED=false`, and set `SSE_REQUIRE_AGENT_ADMITTED=true`. Strict admitted mode excludes direct-stream responses, same-user guard responses, and `AGENT_BUSY` global-capacity responses, so it proves true Agent admission rather than controlled backpressure or the optimized read-only path. Set `SSE_TRANSPORT=post` to regression-test the legacy POST/fetch stream path.

`PERF_PROFILE=agent_saturation` verifies the `/api/ai/smart-chat` concurrency guard. It runs 16 VUs against the endpoint, accepts 200/202 for admitted requests, requires some 503 rejections above the 8-request limit, and requires rejection p95 < 1s. The lower-level Agent service also has a global capacity gate controlled by `app.agent.capacity.max-concurrent` / `AGENT_MAX_CONCURRENT`; over-capacity chat/SSE requests return `AGENT_BUSY` before RAG, database context assembly, or DeepSeek calls.

`PERF_PROFILE=scheduler_soak` verifies scheduler API stability over a long window. The default duration is 30 minutes and the scheduler p95 threshold is < 1s. Use this for release/nightly soak runs; the shorter release wrapper still runs deterministic 10k scheduler query validation in `TaskScheduleRunnerPerformanceIT`.

`PERF_PROFILE=business_api` verifies real authenticated business endpoints without mocking the Spring backend. It covers auth, notes, folders, tags, schedules, task schedules, annotations, typed links, canvases, mind maps, note databases, workflows, media, batch note actions, evaluation, graph, notifications, admin guard, and link preview. The release wrapper runs this last because note writes may trigger asynchronous embedding work and should not contaminate AI/RAG latency gates.

## Commands

Local core profile:

The release wrapper seeds 10,000 `perf-note-*` rows for the verified user automatically. For manual runs, seed and clean the same dataset:

```powershell
$userId = (Get-Content "$env:TEMP\ainote-real-run\user-id.txt" -Raw).Trim()
powershell -ExecutionPolicy Bypass -File scripts\seed-perf-notes.ps1 -UserId $userId -Count 10000
```

```powershell
$env:BASE_URL = 'http://127.0.0.1:8081'
$env:AUTH_TOKEN = '<jwt>'
$env:PERF_PROFILE = 'local_core'
$env:PERF_MIN_NOTES = '10000'
$env:DURATION = '30s'
k6 run perf\ainote-load.js
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts\seed-perf-notes.ps1 -UserId $userId -Cleanup
```

Embedding direct profile:

```powershell
$env:BASE_URL = 'http://127.0.0.1:8081'
$env:AUTH_TOKEN = '<jwt>'
$env:PERF_PROFILE = 'embedding_direct'
$env:EMBEDDING_URL = 'http://127.0.0.1:8082/v1'
$env:EMBEDDING_MODEL = 'Qwen/Qwen3-Embedding-0.6B'
$env:DURATION = '30s'
k6 run perf\ainote-load.js
```

Real LLM profile:

```powershell
$env:BASE_URL = 'http://127.0.0.1:8081'
$env:AUTH_TOKEN = '<jwt>'
$env:PERF_PROFILE = 'llm'
$env:DURATION = '30s'
$env:SSE_VUS = '10'
k6 run perf\ainote-load.js
```

SSE 100 profile:

```powershell
$env:BASE_URL = 'http://127.0.0.1:8081'
$env:AUTH_TOKEN = '<jwt>'
# Optional strict mode: distribute VUs across multiple users and fail if the
# response is the same-user concurrency guard path.
# Prefer a token file for large user counts; it avoids shell/environment length limits.
# $env:AUTH_TOKENS_FILE = 'C:\tmp\sse-tokens.txt'
# For small smoke runs, comma-separated AUTH_TOKENS is also supported.
# $env:AUTH_TOKENS = '<jwt-1>,<jwt-2>,...'
# $env:SSE_REQUIRE_AGENT_ADMITTED = 'true'
# $env:SSE_EXPECT_DIRECT_STREAM = 'true'
$env:PERF_PROFILE = 'sse_100'
$env:DURATION = '30s'
$env:SSE_VUS = '100'
# Optional: use 'post' to test the legacy POST/fetch stream path.
$env:SSE_TRANSPORT = 'eventsource'
# Optional: extend this when validating slow external LLM completion rather than k6 interruption behavior.
# $env:SSE_GRACEFUL_STOP = '120s'
# Optional: extend this when external LLM streams exceed k6's default 60s request timeout.
# $env:SSE_HTTP_TIMEOUT = '180s'
k6 run perf\ainote-load.js
```

Agent saturation profile:

```powershell
$env:BASE_URL = 'http://127.0.0.1:8081'
$env:AUTH_TOKEN = '<jwt>'
$env:PERF_PROFILE = 'agent_saturation'
$env:DURATION = '30s'
$env:AGENT_SATURATION_VUS = '16'
k6 run perf\ainote-load.js
```

Scheduler soak profile:

```powershell
$env:BASE_URL = 'http://127.0.0.1:8081'
$env:AUTH_TOKEN = '<jwt>'
$env:PERF_PROFILE = 'scheduler_soak'
$env:DURATION = '30m'
k6 run perf\ainote-load.js
```

Business API profile:

```powershell
$env:BASE_URL = 'http://127.0.0.1:8081'
$env:AUTH_TOKEN = '<jwt>'
$env:PERF_PROFILE = 'business_api'
$env:BUSINESS_USERNAME = '<verified-username>'
$env:BUSINESS_PASSWORD = '<verified-user-password>'
$env:DURATION = '30s'
k6 run perf\ainote-load.js
```

## Pass Criteria

- Local combined: `http_req_failed < 1%`, notes p95 < 300ms, scheduler p95 < 1000ms, direct TEI embedding p95 < `EMBEDDING_P95_MS` (raw k6 default 2500ms).
- Local core: `http_req_failed < 1%`, notes p95 < 300ms, scheduler p95 < 1000ms.
- Embedding direct: `http_req_failed < 1%`, direct TEI embedding p95 < `EMBEDDING_P95_MS` (raw k6 default 2500ms; release-wrapper default 4500ms).
- LLM: checks must pass, `rag_cold_with_rewrite_latency` p95 must be below `RAG_COLD_REWRITE_P95_MS` (raw k6 default 8000ms), and provider latencies must be reported. DeepSeek route/chat latency is tracked as a release risk, not as a local 800ms SLO.
- SSE 100: `http_req_failed < 5%`, `sse_accepted > 99%`, `sse_heartbeat_received > 99%`, `sse_token_received > 99%`, `sse_complete_received > 99%`, `sse_retry_received > 99%`, `sse_event_id_received > 99%`, first-token p95 < 2000ms, complete p95 < 5000ms, and release-wrapper `jvm.threads.live` delta <= `SseThreadLeakMaxDelta` (default 20). When `SSE_EXPECT_DIRECT_STREAM=true`, `sse_direct_stream > 99%` is required. When `SSE_REQUIRE_AGENT_ADMITTED=true`, `sse_agent_admitted > 99%` is also required, so `DIRECT_STREAM`, same-user concurrency-guard responses, and `AGENT_BUSY` capacity responses no longer count as full agent-stream success.

## Latest admitted-agent result

2026-07-02 strict admitted-agent run:

- Backend: `CHAT_DIRECT_STREAM_ENABLED=false`, `AGENT_MAX_CONCURRENT=120`, `AGENT_EXECUTOR_POOL_SIZE=128`, `SSE_EXECUTOR_POOL_SIZE=128`, `DB_POOL_MAX_SIZE=80`, `DEEPSEEK_MAX_TOKENS=128`
- k6: `PERF_PROFILE=sse_100`, `SSE_VUS=100`, `DURATION=30s`, `SSE_REQUIRE_AGENT_ADMITTED=true`, `AUTH_TOKENS_FILE=<100 user tokens>`, `SSE_MESSAGE=ok`
- Result: 1323/1323 accepted, 1323/1323 admitted, `AGENT_BUSY=0`, `DIRECT_STREAM=0`, `http_req_failed=0%`, complete p95=1708.41ms, first-byte p95=246.14ms
- Agent saturation: handled rate > 99%, rejection rate > 0, 503 rejection p95 < 1000ms, and `http_req_failed < 1%` with 503 marked as an expected response.
- Scheduler soak: `http_req_failed < 1%`, scheduler p95 < 1000ms for the configured duration. The default manual/nightly duration is 30 minutes.
- Business API: `http_req_failed < 1%`, `business_api_success > 99%`, overall business p95 < 1000ms, most endpoint-family p95 values < 1000ms, media/graph p95 < 2000ms, and batch p95 < 1500ms.
- 10k local RAG: enforced by backend `RagVectorIndexPerformanceIT`, which seeds 10,000 1024-dimensional vectors, asserts `idx_l4j_embeddings_vec` appears in the execution plan, and requires p95 < 800ms.
- Scheduler 10k query shape: enforced by backend `TaskScheduleRunnerPerformanceIT`, which seeds 10,000 schedules, asserts `idx_task_schedules_next_run` appears in the execution plan, and requires 30 measured scans p95 < 1000ms.
- Release: `scripts\verify-release.ps1` runs Docker-backed backend verify, frontend gates, real AI smoke, local core k6, direct embedding k6, SSE 100, LLM k6, Agent saturation, and Business API. Add `-RunSchedulerSoak` for the 30-minute scheduler soak gate.
