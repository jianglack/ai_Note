# Incident Response Runbook

## Severity And Acknowledgement

| Severity | Definition | Acknowledge | Communication cadence |
| --- | --- | --- | --- |
| SEV-1 | Broad outage, security/privacy impact, or unrecoverable data risk | 5 minutes | Every 30 minutes |
| SEV-2 | Material degradation, sustained SLO breach, or critical dependency failure | 15 minutes | Every 60 minutes |
| SEV-3 | Limited degradation or non-urgent reliability risk | 4 business hours | At material changes |

A real schedule, paging destination, phone number, and escalation provider must
be supplied by the deployment owner. Repository examples never contain them.

## Roles

- Incident commander: owns severity, priorities, and final resolution decision.
- Operations lead: performs mitigation and validates service recovery.
- Communications lead: publishes sanitized status updates at the required cadence.
- Scribe: keeps an immutable UTC timeline, decisions, evidence references, and owners.

One person may hold multiple roles for a small incident, but incident command
must remain explicit.

## Lifecycle

1. Acknowledge the alert and record UTC start time, alert labels, affected scope,
   and reporter. Do not copy user content or secrets.
2. Classify severity using current user impact, data risk, duration, and blast radius.
3. Assign incident commander and open the deployment-defined incident channel.
4. Stabilize first: stop rollout, disable a feature, shed load, fail over, or
   restore from the relevant runbook.
5. Preserve sanitized logs, trace IDs, metric screenshots/queries, deployment
   identifiers, configuration hashes, and command outcomes.
6. Communicate known impact, current mitigation, next update time, and uncertainty.
7. Validate recovery through user-facing health, alert resolution, data-integrity
   checks, and a controlled synthetic probe.
8. Close only after monitoring remains healthy for an agreed observation window
   and follow-up owners and deadlines are recorded.

## Escalation

- Escalate immediately to SEV-1 for confirmed security/privacy impact, destructive
  data risk, or broad unavailability.
- Escalate when acknowledgement targets expire, mitigation increases risk, or the
  responsible service owner cannot be reached.
- Invoke `BACKUP_RESTORE.md` only with explicit incident-command authorization and
  verified backup evidence.
- Preserve fail-closed security and memory-governance controls during mitigation.

## Status Update Template

```text
UTC time:
Severity and incident ID:
Observed impact:
Known facts:
Current mitigation:
Risks and unknowns:
Next update time:
Incident commander:
```

## Post-Incident Review

Complete the review within five business days for SEV-1/SEV-2 and significant
near misses. Record:

- impact, duration, and detection source;
- UTC timeline and decision points;
- root cause and contributing conditions;
- what worked and what delayed detection or mitigation;
- missing tests, alerts, dashboards, runbooks, or ownership;
- corrective actions with one owner and due date each;
- replay, regression, security, or capacity cases added before closure.

The review is blameless and evidence-based. It must not contain credentials,
raw personal data, private prompts, authentication cookies, or real contact details.
