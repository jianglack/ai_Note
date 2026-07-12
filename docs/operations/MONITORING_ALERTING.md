# Monitoring And Alerting Runbook

## Scope

This runbook operates the managed-local Prometheus, Alertmanager, and Grafana
assets under `observability/`. It defines open-source deployment contracts and
does not claim that a real paging provider or staffed rotation exists.

## Deployment

1. Generate a high-entropy Prometheus scrape token outside the repository.
2. Provide the same value to the backend as `PROMETHEUS_SCRAPE_TOKEN` and to
   Prometheus through `observability/secrets/prometheus_scrape_token`.
3. Copy `alertmanager.production.yml.example` to the ignored
   `alertmanager.production.yml` and mount real receiver URLs as deployment
   secrets. Never write webhook URLs into version control.
4. Set `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` through the deployment
   secret store.
5. Update `prometheus/targets/ainote.json` for the deployed backend address.
6. Validate configurations and rules before `docker compose up -d`.

Production exposes only `/actuator/health/**` and `/actuator/prometheus`.
Prometheus requests require `X-AiNote-Monitoring-Token`; missing configuration
fails with 503 and an invalid token fails with 401.

## Verification

Run the isolated delivery drill from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-monitoring-incident-drill.ps1
```

The drill must report `PASSED`, deliver one firing and one resolved notification,
and leave no `ainote-observability-*` resources. A missing Docker Engine,
promtool, amtool, Grafana instance, receiver, or cleanup step is a failed gate.

## Alert Response

For every alert: acknowledge it at the severity target, inspect the linked SLO,
preserve UTC evidence, assign an owner, and follow `INCIDENT_RESPONSE.md` when
user impact or data risk is possible.

### AiNoteBackendDown

Confirm target reachability, DNS, TLS, backend process state, and scrape-token
alignment. Do not weaken endpoint authentication. If users are affected, stop
rollouts and classify at least SEV-2; broad outage is SEV-1.

### AiNoteHighHttp5xxRatio

Break down `http_server_requests_seconds_count` by URI, status, and exception.
Compare deployment time, dependency health, and database saturation. Roll back
the latest change when causality is credible.

### AiNoteHighHttpP95Latency

Inspect request-rate changes, slow URI groups, JVM pressure, Hikari pending
connections, downstream model latency, and host saturation. Do not increase
timeouts before identifying the queue or dependency causing delay.

### AiNoteJvmHeapPressure

Inspect live-set trend, allocation rate, GC pauses, and recent workload changes.
Capture a sanitized heap diagnostic only under the security evidence policy.

### AiNoteDatabasePoolPending

Inspect active, idle, pending, and maximum Hikari connections, PostgreSQL locks,
slow queries, and transaction duration. Raising pool size requires database
capacity evidence.

### AiNoteAgentCapacityRejected

Inspect active Agent operations, queue delay, provider latency, and admission
limits. Preserve bounded rejection rather than allowing unbounded concurrency.

### AiNoteChatTotalFailure

Inspect the trace ID, provider/circuit-breaker state, and fallback outcomes.
Never include raw prompts, responses, cookies, or API keys in incident evidence.

### AiNoteMemoryFlushFailure

Treat as a data-integrity incident. Stop risky releases, inspect PostgreSQL and
transaction retries, and verify sequence continuity before declaring recovery.

### AiNoteMemoryAdvisorFailureRatio

Check advisor availability, prompt version, parser failures, and fallback rule
decisions. Keep the advisor fail-closed and use replay evaluation before tuning.

### AiNotePrometheusRuleEvaluationFailure

Run `promtool check rules` and `promtool test rules`, inspect Prometheus logs,
and restore rule evaluation before trusting absence of service alerts.

### AiNoteAlertmanagerNotificationFailure

Inspect receiver reachability, provider response, routing, inhibition, and secret
mounts. Use a synthetic alert after repair and verify both firing and resolved
delivery.

## Retention And Review

Telemetry retention, external log storage, paging-provider retention, and access
control are deployment responsibilities. Review thresholds after incidents and
scheduled capacity tests; changes require rule tests, dashboard alignment, and
a documented owner.
