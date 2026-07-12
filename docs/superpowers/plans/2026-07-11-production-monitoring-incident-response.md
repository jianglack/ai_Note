# Production Monitoring, Alerting, And Incident Response Plan

## Execution sequence

1. Record the audit, architecture, severity policy, evidence model, and non-claims.
2. Add a dedicated Prometheus scrape-token filter with constant-time comparison and focused tests.
3. Expose only health and Prometheus in production; enable probes and ECS structured logs.
4. Add Prometheus scrape configuration, recording/alert rules, and deterministic rule tests.
5. Add Alertmanager route/inhibition configuration and a secret-free receiver template.
6. Add Grafana datasource/dashboard provisioning and an operator dashboard.
7. Add a disposable managed-local drill that validates configuration, fires and resolves a synthetic alert, verifies webhook delivery, and cleans up.
8. Add monitoring, on-call, incident response, communication, and postmortem runbooks.
9. Add repository asset tests that fail for missing rules, runbooks, secret handling, cleanup, or production overclaims.
10. Run targeted tests, full regressions where required, the observability drill, parser checks, and final audit.

## Safety constraints

- Use only generated `ainote-observability-*` resources.
- Do not restart or scrape the existing backend during destructive drill steps.
- Generate all drill tokens and receiver endpoints at runtime.
- Never store notification credentials, application JWTs, cookies, raw user content, or real incident contact details.
- Fail closed when Docker, promtool, amtool, Grafana validation, the webhook receiver, or cleanup is unavailable.

## Release blockers

- Prometheus endpoint is public without the monitoring token.
- Health details become public.
- A critical alert cannot route or deliver.
- Rule/config validation is unavailable or reports errors.
- Dashboard provisioning is invalid.
- Any temporary secret appears in the final report.
- Cleanup leaves a drill process, container, or network.
- Documentation claims a real 24x7 rotation or production proof without evidence.
