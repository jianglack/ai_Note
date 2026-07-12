# AiNote Security Verification Runbook

## Purpose

Use this runbook before an open-source release and after authentication, authorization, dependency, container, network-fetching, or agent-tool changes. All generated reports are managed-local evidence and must retain `productionProven=false`.

## Prerequisites

- Windows PowerShell 5.1 or newer.
- Docker Engine.
- Java 17 and Maven.
- Node.js 24 and npm.
- The verified OSV Scanner 2.4.0 binary and its release `SHA256SUMS` under `backend/target/security-tools/osv-scanner-v2.4.0/`.
- No requirement to stop the normal AiNote backend. Dynamic tests create only `ainote-security-*` resources and use random loopback ports.

Never place passwords, tokens, cookies, CSRF values, API keys, raw notes, or complete user identifiers in reports or tickets.

## 1. Regression gates

```powershell
Set-Location backend
mvn -B verify

Set-Location ..\frontend
npm ci
npm audit --omit=dev --audit-level=high --registry=https://registry.npmjs.org
npm run test:coverage
npm run lint
npm run build
```

If Windows locks `backend/target/backend-0.1.0.jar` because the development backend is running, run Maven in an isolated repository-layout copy. Do not stop or overwrite the running service halfway through verification; copy back only a fully verified artifact.

## 2. Supply-chain gate and SBOMs

```powershell
Set-Location <repository-root>
.\scripts\run-security-sca.ps1
```

The command passes only when npm audit is available with zero high/critical findings, OSV Scanner is available and checksum-verified, both CycloneDX SBOMs are parseable, and no unaccepted vulnerability remains. Review `backend/target/security-compliance/supply-chain-security-report.json`.

The single Jackson exception is package-, version-, advisory-, severity-, owner-, and expiry-bound. It expires on 2026-08-11. Remove or renew it only after a documented review; scanner failure is never equivalent to zero findings.

## 3. Isolated dynamic penetration gate

Build the verified backend JAR, then run:

```powershell
.\scripts\run-security-compliance.ps1
```

The runner creates disposable PostgreSQL and Redis containers, generated users, and a production-profile backend. It verifies authentication, CSRF, BOLA, administrator denial, generic errors, SSRF, CORS, method handling, production exposure, security headers, logout revocation, and rate limiting.

Pass criteria:

- `status=PASSED`
- `qualityGatePassed=true`
- `failureCount=0`
- `cleanupPassed=true`
- no remaining container whose generated name begins with the run ID

Reports are written to `backend/target/security-compliance/ainote-security-*/security-compliance-report.json`.

## 4. Container runtime checks

```powershell
docker build -t ainote-security-backend:local backend
docker build -t ainote-security-frontend:local frontend
docker run --rm --entrypoint id ainote-security-backend:local
docker run --rm --entrypoint id ainote-security-frontend:local
```

Both IDs must be non-zero. The frontend Nginx configuration resolves the deployment hostname `backend` at startup. A standalone smoke test must therefore attach the normal Compose network or provide a temporary `backend` host alias. Verify an HTTP 200 response and these headers: `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, and `Permissions-Policy`.

## 5. Evidence review

1. Confirm report schema, timestamps, hashes, scanner versions, counts, and cleanup state.
2. Search reports and logs for secrets before sharing them.
3. Compare results with `docs/security/security-compliance-matrix.md`.
4. Treat every new endpoint, tool, external fetch, dependency exception, or trust boundary as a matrix update trigger.
5. Preserve reports as CI/release artifacts; do not commit generated `target/` evidence.

## Failure response

- Stop the release on any failed mandatory control, unavailable scanner, unaccepted high/critical vulnerability, cross-tenant access, authentication bypass, secret exposure, root runtime, or cleanup failure.
- Retain the sanitized failure report and exact commit identifier.
- Classify the root cause as product defect, test defect, data/environment defect, or external control failure.
- Add a regression test before closing a product defect.
- Re-run the narrow failed gate first, then the complete security gate.

## Explicit non-claims

Passing this runbook does not prove production TLS, WAF/DDoS defenses, cloud IAM, KMS/HSM custody, host hardening, network segmentation, third-party processor compliance, legal compliance, SOC 2/ISO 27001 certification, or an independent manual penetration test.
