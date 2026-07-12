# Security Penetration and Compliance Post-implementation Review

## Decision

**PASSED for managed-local open-source security readiness.** Every mandatory acceptance criterion in the design is implemented and backed by repeatable evidence. The result remains `productionProven=false` and is not an independent penetration-test certificate or legal/regulatory attestation.

## Delivered controls

1. Browser authentication is cookie-only: JWTs are absent from auth JSON, frontend state, and local storage; explicit bearer clients remain supported.
2. Cookie-authenticated unsafe requests require CSRF validation, with a bootstrap endpoint and frontend refresh/retry contract.
3. Public auth routes and CORS origins are exact and fail closed; production Swagger and non-health actuator endpoints remain unavailable.
4. New/reset passwords require 15 Unicode code points, login attempts are rate-limited, and logout revokes server token state.
5. Tenant BOLA, administrator access, generic errors, SSRF, and production security headers are covered by isolated dynamic attacks.
6. Frontend and backend dependencies were upgraded; npm audit has zero findings and OSV has zero unaccepted findings.
7. Backend and frontend CycloneDX SBOMs, fail-closed CI gates, pinned actions, secret scanning, dependency review, and Dependabot are present.
8. Both runtime images are digest-pinned and execute as non-root users.
9. The compliance matrix records `PASS`, `PARTIAL`, and `EXTERNAL` boundaries with evidence and explicit non-claims.
10. The maintainer runbook defines repeatable execution, evidence review, exception expiry, and failure response.

## Final evidence

### Dynamic penetration report

- Report: `backend/target/security-compliance/ainote-security-20260711T091952Z-62428/security-compliance-report.json`
- Status: `PASSED`
- Quality gate: `true`
- Evidence class: `managed-local`
- Production proven: `false`
- Tests: 25 passed, 0 failed
- Cleanup passed: `true`

### Supply-chain report

- Report: `backend/target/security-compliance/supply-chain-security-report.json`
- Status: `PASSED`
- npm audit: 0 total, 0 high, 0 critical
- OSV: 1 raw reviewed finding, 1 accepted time-bounded exception, 0 gated findings
- Scanner: OSV Scanner 2.4.0, verified SHA-256 `0cdd113610126d5dfd5e12ad0e0b4f3e879291ff19bb43b0c52ed2f2c2df1a37`
- Exception: `GHSA-5jmj-h7xm-6q6v`, exact Jackson 2.21.5 moderate record, expires 2026-08-11
- SBOMs: backend 257 packages; frontend 580 packages

### Regression and runtime evidence

- Backend isolated full verify: 1,020 tests, 0 failures, 0 errors; JaCoCo gate passed.
- Frontend Vitest: 23 files and 80 tests passed; 34 security checks passed; production build passed.
- Frontend lint: 0 errors and 44 pre-existing React hook warnings.
- Backend fat JAR: 289,826,029 bytes; main backend remained `UP` during verification.
- Runtime images: backend `uid=100(ainote)` and frontend `uid=101(nginx)`.
- Frontend runtime smoke: HTTP 200 with CSP, MIME, anti-framing, referrer, and permissions headers; temporary container cleanup passed.

## Problems found and corrected

1. Browser JWTs were returned in JSON and persisted in local storage. The browser contract now uses HttpOnly cookies and authenticated `/me` bootstrap only.
2. Cookie authentication ran with CSRF disabled. Cookie requests now require CSRF while explicit bearer requests remain exempt.
3. `/api/auth/**` was publicly permitted and CORS validation was too permissive. Exact route and origin validation replaced both patterns.
4. Six-character passwords were accepted. Registration and reset now require 15 Unicode code points.
5. Frontend audit initially reported high-severity vulnerable dependencies. Direct and transitive dependencies were upgraded until official npm audit reached zero.
6. Backend SCA initially timed out during vulnerability database synchronization. The local gate uses checksum-verified OSV scanning. On 2026-07-12 CI was aligned to the same OSV 2.4.0 policy after cached Dependency-Check runs remained stuck or failed during NVD synchronization; scanner unavailability still fails closed.
7. Initial OSV scanning found 25 affected Maven packages and 82 vulnerability records. Dependency upgrades reduced this to one reviewed Jackson 2.21.5 false-positive boundary record and zero gated findings.
8. Runtime containers lacked explicit non-root users and frontend security headers. Both images and Nginx were hardened and verified at runtime.
9. The first standalone frontend smoke container could not resolve its expected Compose upstream hostname `backend`. Runtime verification now supplies the deployment DNS contract explicitly; the runbook records this requirement.
10. Dynamic runner defects involving PowerShell HTTP assembly behavior, CSRF refresh, filter ordering, and 404 mapping were found during execution and corrected before the final 25/25 pass.
11. Windows locked the normal Maven repackage target while the development backend was running. A complete isolated repository-layout verify produced the final fat JAR without interrupting the service.

## Residual boundaries

- MFA and compromised-password screening remain product enhancements.
- TLS termination, WAF/DDoS protection, cloud IAM, KMS/HSM, host/container-runtime policy, egress filtering, network segmentation, branch protection, and off-host evidence retention require deployment verification.
- The dependency exception expires on 2026-08-11 and must be removed or explicitly re-reviewed.
- Independent manual penetration testing, legal review, GDPR/PIPL conclusions, SOC 2, and ISO 27001 certification are not claimed.

## Final conclusion

The third production-readiness item is complete at the agreed managed-local open-source standard: design, plan, pre-review, remediation, automated penetration testing, dependency analysis, runtime verification, compliance mapping, and post-review are all present. A future real deployment must execute the external rows in the matrix before claiming production proof.
