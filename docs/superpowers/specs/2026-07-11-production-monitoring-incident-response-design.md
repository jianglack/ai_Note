# Production Monitoring, Alerting, And Incident Response Design

## 1. Objective

Establish a repeatable managed-local operations stack for AiNote: secure metric collection, versioned service-level alerts, provisioned dashboards, severity-based notification routing, on-call ownership, incident command, and a synthetic end-to-end alert drill.

The result is open-source operational readiness evidence. It is not proof that a real production notification provider, staffing schedule, or incident response organization is active.

## 2. Authoritative references

- Spring Boot Actuator endpoint security and Prometheus exposure: <https://docs.spring.io/spring-boot/reference/actuator/endpoints.html>
- Spring Boot structured logging: <https://docs.spring.io/spring-boot/reference/features/logging.html>
- Prometheus alerting rules: <https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/>
- Prometheus HTTP scrape configuration: <https://prometheus.io/docs/prometheus/latest/configuration/configuration/>
- Alertmanager routing, grouping, inhibition, and receivers: <https://prometheus.io/docs/alerting/latest/configuration/>
- Grafana file provisioning: <https://grafana.com/docs/grafana/latest/administration/provisioning/>

## 3. Audit findings

### Existing strengths

- Actuator, Micrometer, and the Prometheus registry are dependencies.
- JVM, HTTP server, Hikari, agent, chat, long-term memory, and short-term memory metrics already exist.
- Engineering SLOs and a managed-local capacity report define availability, latency, saturation, and integrity thresholds.
- Health, rate-limit, circuit-breaker, audit, and trace behavior have automated coverage.

### Confirmed gaps

1. The production profile exposes only `health`; `/actuator/prometheus` is absent and cannot be scraped.
2. The application has no dedicated non-user credential for metric collection.
3. There are no versioned Prometheus recording/alert rules or rule unit tests.
4. There is no provisioned Grafana operations dashboard.
5. There is no Alertmanager routing, grouping, inhibition, or receiver template.
6. No alert proves notification delivery and resolution through a disposable stack.
7. There is no severity policy, on-call handoff, incident command checklist, communication cadence, or post-incident review template.
8. Production console logs are not explicitly configured as structured JSON.

## 4. Architecture

### Application telemetry

- Production exposes only `health` and `prometheus` Actuator endpoints.
- Health details remain hidden. Liveness and readiness probes are enabled.
- `/actuator/prometheus` requires `X-AiNote-Monitoring-Token` and rejects blank, missing, or incorrect values.
- The token is loaded from an environment secret and compared in constant time. It must never be committed, logged, included in reports, or used as an end-user JWT.
- Production console logs use Spring Boot ECS structured JSON so timestamps, severity, logger, process, service, and MDC fields remain machine-parseable.

### Prometheus

- Configuration and alert rules are version-controlled.
- The scrape token is read from a mounted secret file using Prometheus custom `http_headers.files`; the token is not embedded in configuration.
- Rules cover target availability, HTTP error ratio, p95 latency, JVM heap pressure, Hikari pending connections, agent-capacity rejection, total chat failure, memory flush failure, advisor failure, and observability pipeline health.
- Alerts use bounded `for` durations, severity, service, SLO, runbook, and owner labels. Low-volume ratios require a minimum request rate to avoid unstable division.

### Alertmanager

- `critical` and `warning` routes are grouped by service and alert name.
- Critical alerts have shorter repeat intervals and are never inhibited by warnings.
- Instance-level symptom alerts are inhibited while the whole backend target is down.
- Receiver URLs are generated from deployment secrets. The committed file is a template, not a real destination.

### Grafana

- Prometheus datasource and the operations dashboard are provisioned from files.
- Dashboard panels show availability, request rate, 5xx ratio, p95 latency, target state, JVM heap, Hikari pressure, agent saturation, chat failures, memory failures, and active alerts.
- Dashboard thresholds mirror versioned rules; the dashboard is explanatory evidence, not the source of release thresholds.

## 5. Severity and ownership

| Severity | Meaning | Acknowledge target | Initial action |
| --- | --- | --- | --- |
| SEV-1 | Broad outage, security/privacy impact, or unrecoverable data risk | 5 minutes | Page primary and secondary; appoint incident commander; stop risky changes |
| SEV-2 | Material degradation, sustained SLO breach, or critical dependency failure | 15 minutes | Page primary; start incident channel and mitigation |
| SEV-3 | Limited degradation, warning saturation, or non-urgent reliability risk | 4 business hours | Create tracked issue and assign owner |

Repository owners define the role contract. A real schedule, phone number, chat channel, and paging provider are deployment-specific external controls.

## 6. Incident lifecycle

1. Detect and deduplicate the alert.
2. Acknowledge and assign incident commander, operations lead, communications lead, and scribe as scale requires.
3. Classify severity using user impact, data risk, duration, and scope.
4. Stabilize first: stop rollout, disable a feature, shed load, fail over, or restore according to the relevant runbook.
5. Preserve sanitized evidence and a UTC event timeline.
6. Communicate at the severity cadence without exposing secrets or user content.
7. Resolve only after the alert clears and user-facing health is verified.
8. Complete a blameless review with root cause, contributing factors, detection gaps, corrective owners, and due dates.

## 7. Drill and evidence model

The managed-local drill must:

- validate Prometheus configuration and all rules using the pinned official `promtool`;
- execute rule unit tests for firing and non-firing boundaries;
- validate Alertmanager configuration in UTF-8 strict mode;
- validate Grafana provisioning and dashboard JSON;
- start disposable Prometheus, Alertmanager, webhook receiver, and Grafana resources using generated `ainote-observability-*` names;
- inject a synthetic critical alert, prove it reaches Alertmanager and the webhook receiver, send a resolved state, and prove a resolved notification is delivered;
- verify no committed secret appears in configuration or final evidence;
- remove every disposable process, container, network, temporary config, and generated secret;
- emit a machine-readable report with `evidenceClass=managed-local` and `productionProven=false`.

## 8. Acceptance criteria

1. Production health and Prometheus exposure is minimal and tested.
2. Metric scraping requires a dedicated constant-time verified token.
3. Alert rules parse and pass deterministic rule tests.
4. Alertmanager routes and inhibition parse in UTF-8 strict mode.
5. Grafana datasource/dashboard provisioning parses and loads.
6. The synthetic firing and resolved notifications both reach the disposable receiver.
7. Operations and incident runbooks define severity, ownership, acknowledgement, escalation, communication, evidence, resolution, and review.
8. Backend regressions, configuration tests, and `git diff --check` pass.
9. The final review lists external staffing and provider boundaries without claiming production proof.

## 9. Explicit non-claims

Managed-local evidence does not prove a 24x7 staffed rotation, phone/SMS/email/chat delivery, paging-provider SLA, production telemetry retention, multi-region monitoring, real user-impact detection, external log storage, or completed real-production incident response.
