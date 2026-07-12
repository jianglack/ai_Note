# AiNote Managed-Local Security Compliance Matrix

## Review scope

This matrix records the technical subset verified for the AiNote open-source release. It is based on OWASP ASVS 5.0.0 Level 2-inspired controls, OWASP API Security Top 10 2023, NIST SSDF 1.1, and NIST SP 800-63B-4.

Status meanings:

- `PASS`: implemented and backed by repeatable local or CI evidence.
- `PARTIAL`: a control exists, but deployment or product work remains.
- `EXTERNAL`: the control belongs to the production hosting or organizational environment.
- `NOT_APPLICABLE`: reviewed and outside this release's architecture or scope.

This is a scoped engineering review. It is not a full ASVS attestation, independent penetration-test certificate, legal opinion, or regulatory certification.

## Control matrix

| ID | Baseline area | Requirement | Status | Evidence | Residual boundary |
|---|---|---|---|---|---|
| GOV-01 | ASVS V1 / SSDF PO | Versioned threat model, acceptance criteria, and explicit non-claims | PASS | `docs/superpowers/specs/2026-07-11-security-penetration-compliance-design.md`; pre- and post-implementation reviews | Reassess whenever trust boundaries or sensitive data classes change |
| AUTH-01 | ASVS V6 / API2 | Browser JWT is absent from response JSON and JavaScript persistence | PASS | `AuthResponseSecurityTest`; `AuthControllerIntegrationTest`; dynamic test `jwt_absent_from_auth_json`; `docs/security/auth-token-storage.md` | Native clients may use explicit bearer authentication |
| AUTH-02 | ASVS V3 / API2 | Cookie-authenticated unsafe requests require CSRF validation | PASS | `SecurityConfigHeadersTest`; `AuthControllerIntegrationTest`; dynamic tests `csrf_missing_rejected` and `csrf_valid_request_accepted` | TLS and `Secure` cookie transport are deployment responsibilities |
| AUTH-03 | NIST 800-63B-4 | New and reset passwords require at least 15 Unicode code points | PASS | request DTO validation tests and frontend auth-page behavior tests | Compromised-password screening and MFA are future product controls |
| AUTH-04 | ASVS V6 / API2 | Authentication attempts are limited and logout revokes server-side token state | PASS | dynamic tests `login_rate_limit` and `logout_revokes_server_token` | Distributed rate limiting depends on production Redis availability and topology |
| AUTH-05 | ASVS V6 | Multi-factor authentication and recovery assurance | PARTIAL | Password reset paths are tested; no MFA factor is implemented | Required before claiming higher-assurance identity or privileged-admin readiness |
| ACCESS-01 | ASVS V8 / API1 | Tenant object access fails closed for cross-user read, update, and delete | PASS | dynamic BOLA tests, including owner-object integrity check; tenant-scoped repository/service tests | Re-run whenever a new identifier-based endpoint is introduced |
| ACCESS-02 | ASVS V8 / API5 | Administrator endpoints require the configured allow-list and audit access | PASS | `AdminAccessGuardTest`; dynamic test `ordinary_user_admin_denied` | Enterprise IAM, role lifecycle, and break-glass access are external |
| INPUT-01 | ASVS V5 / API8 | Malformed input and application errors do not expose implementation details | PASS | `GlobalExceptionHandlerTest`; dynamic tests `malformed_json_rejected` and `generic_error_has_no_stack_trace` | Internet-edge request normalization is external |
| INPUT-02 | ASVS V5 / API7 | SSRF controls reject loopback/private destinations without opening a socket | PASS | dynamic loopback canary `ssrf_loopback_no_outbound_connection`; service tests | DNS, egress firewall, and proxy enforcement should provide a second production layer |
| CLIENT-01 | ASVS V3/V14 | Browser responses publish CSP, anti-framing, MIME, referrer, and permissions headers | PASS | runtime container HTTP check; `SecurityDeploymentAssetsTest`; `frontend/nginx.conf` | CSP must be reviewed when adding third-party origins or scripts |
| CONFIG-01 | ASVS V14 / API8 | Exact-origin CORS, disabled production Swagger, and health-only actuator exposure | PASS | configuration tests; dynamic CORS, Swagger, and actuator tests | Production reverse-proxy and TLS configuration are external |
| LLM-01 | API10 / project threat model | Untrusted prompt content and tool execution pass through independent guardrails | PASS | `InputGuardrailTest`; `AgentServiceGuardrailTest`; `PreExecutionGuardTest`; `ToolExecutionPipelineTest` | New tools require threat review and scoped authorization tests |
| SUPPLY-01 | SSDF PS/PW | High/critical dependency findings fail the release gate | PASS | `scripts/run-security-sca.ps1`; `.github/workflows/ci.yml`; `supply-chain-security-report.json` | The reviewed Jackson exception expires on 2026-08-11 and must not auto-renew |
| SUPPLY-02 | SSDF PS/PW | Backend and frontend CycloneDX SBOMs are generated as release evidence | PASS | `backend/target/bom.json`; `frontend/target/bom.json`; CI artifact upload | Signed release attestations and registry provenance are future controls |
| SUPPLY-03 | SSDF PO/PS | CI uses least privilege, pinned actions, secret scanning, and dependency review | PASS | `.github/workflows/ci.yml`; `.github/dependabot.yml` | Branch protection and required-check enforcement depend on repository settings |
| RUNTIME-01 | ASVS V14 / SSDF PS | Backend and frontend runtime containers execute as non-root users | PASS | image inspection and runtime `id`; `SecurityDeploymentAssetsTest` | Host kernel, runtime policy, seccomp, and orchestration controls are external |
| TEST-01 | ASVS verification | Isolated dynamic penetration suite passes and cleans disposable resources | PASS | `backend/target/security-compliance/ainote-security-20260711T091952Z-62428/security-compliance-report.json`: 25/25, cleanup passed | Managed-local coverage is not an independent manual penetration test |
| DATA-01 | ASVS V8/V12 | Backup encryption, restore integrity, and privacy deletion controls are tested | PASS | backup/restore drill report; memory privacy service/controller tests | KMS custody, immutable off-host storage, and legal retention approval are external |
| OPS-01 | SSDF RV | Vulnerability response, periodic scanning, and exception expiry are operationalized | PARTIAL | CI gates, Dependabot, scan runbook, time-bounded exception | Maintainer response SLA and public security advisory process still require governance |
| EDGE-01 | Deployment | TLS termination, WAF, DDoS, cloud IAM, KMS/HSM, network segmentation, and host hardening | EXTERNAL | Explicit non-claims in design and final review | Must be verified in the chosen production platform |
| LEGAL-01 | Legal/compliance | GDPR, PIPL, SOC 2, ISO 27001, and sector-specific legal conclusions | EXTERNAL | Explicit non-claim | Requires qualified legal/compliance and independent audit work |

## Gate decision

The managed-local engineering gate passes because every mandatory application and supply-chain acceptance criterion is `PASS`, all residual controls have an explicit owner boundary, and no status is unexplained. `PARTIAL` and `EXTERNAL` rows prohibit a claim that the deployment is production-proven or formally certified.
