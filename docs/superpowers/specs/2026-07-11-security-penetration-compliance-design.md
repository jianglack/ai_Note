# AiNote Security Verification and Compliance Design

## 1. Objective

Establish a repeatable managed-local security gate for the AiNote open-source project. The gate combines threat-driven code review, software-composition analysis, isolated dynamic API tests, container/configuration checks, SBOM generation, and a traceable compliance matrix.

This is engineering evidence, not a third-party penetration-test certificate, legal opinion, or regulatory attestation.

## 2. Authoritative baselines

- OWASP ASVS 5.0.0 for web application control verification: <https://owasp.org/www-project-application-security-verification-standard/>
- OWASP API Security Top 10 2023 for API threat coverage: <https://owasp.org/API-Security/editions/2023/en/0x03-introduction/>
- NIST SP 800-218 SSDF 1.1 for secure development and release practices: <https://csrc.nist.gov/pubs/sp/800/218/final>
- NIST SP 800-63B-4 for password and session guidance: <https://pages.nist.gov/800-63-4/sp800-63b.html>

The target is an ASVS Level 2-inspired technical subset appropriate to this application. The project must not claim full ASVS conformance unless every applicable requirement is independently mapped and verified.

## 3. Threat model

### Protected assets

- User credentials and session tokens.
- Notes, media, chat history, long-term memory, review cases, and audit events.
- Administrator-only metrics, evaluations, privacy operations, and destructive actions.
- LLM/API credentials and deployment secrets.
- Build dependencies, release artifacts, and backup packages.

### Trust boundaries

- Browser to Nginx/frontend.
- Browser or API client to Spring Boot.
- Spring Boot to PostgreSQL, Redis, Neo4j, model providers, and fetched web content.
- CI runner to package registries, vulnerability databases, and release storage.

### Required attack coverage

- Broken object and function authorization, including cross-tenant identifiers.
- Authentication bypass, token theft, session fixation, logout/revocation, CSRF, and brute force.
- Injection, stored/reflected XSS, unsafe rendering, malformed JSON, and oversized requests.
- SSRF, DNS rebinding controls, unsafe redirects, and external API failures.
- Security misconfiguration, verbose errors, exposed documentation/actuator endpoints, unsafe CORS, and missing headers.
- Vulnerable dependencies, secret leakage, root containers, and missing release evidence.
- LLM prompt injection and unsafe tool execution are covered by existing agent guardrails and remain in the compliance matrix.

## 4. Mandatory remediations

### Authentication and browser session

1. JWT must be delivered only through `HttpOnly`, `Secure` production cookies and must not appear in JSON, Zustand persistence, `localStorage`, logs, or URLs.
2. Cookie-authenticated unsafe methods require CSRF validation. Bearer-token API clients may be explicitly exempt because browsers do not attach bearer headers automatically.
3. A dedicated CSRF bootstrap endpoint and frontend retry path must support initial login and token renewal.
4. Authentication routing must permit only the exact public endpoints; `/api/auth/**` must not be a blanket allow rule.
5. Page reload must revalidate the cookie using an authenticated current-user endpoint.
6. New and reset passwords require at least 15 Unicode code points and permit at least 100 characters. Existing hashes remain valid until the password changes.

### API and deployment

1. CORS accepts exact configured HTTP(S) origins only; wildcards, paths, user info, and malformed origins fail startup.
2. Production API documentation remains disabled and only redacted health is public.
3. Frontend Nginx emits CSP, anti-framing, MIME sniffing, referrer, permissions, and cache controls.
4. Backend and frontend runtime containers run as non-root users.
5. Authentication rate limiting, request-size controls, generic errors, SSRF fixed-address fetching, tenant-scoped repositories, and admin allow-list auditing are regression-tested.

### Supply chain

1. Official-registry `npm audit` must report zero high or critical vulnerabilities.
2. Backend SCA must produce a parseable report. Scanner timeout/unavailability is a failed gate, not zero findings.
3. High/critical findings require upgrade or a documented, time-bounded exception with exploitability evidence and owner.
4. Backend and frontend CycloneDX SBOMs are release evidence.
5. CI runs secret scanning, SCA, tests, and SBOM generation with least-privilege permissions and cached vulnerability data.

## 5. Dynamic verification topology

The penetration runner creates disposable PostgreSQL and Redis containers, starts an isolated production-profile backend, and uses two ordinary users plus an optional configured admin identity. It never attacks the running development backend or existing data containers.

Dynamic cases include:

- Public health allowed; protected API unauthenticated request denied.
- Login/register response sets secure cookie without returning token JSON.
- Unsafe cookie-authenticated request without CSRF rejected; valid token accepted.
- Bearer authentication remains supported for non-browser API clients.
- Cross-user read, update, delete, media, memory, and selected-note identifiers denied without revealing ownership.
- Ordinary user denied every sampled administrator endpoint.
- Logout revokes the server-side token and clears the cookie.
- Login injection payload does not authenticate; repeated failures reach 429.
- Oversized/malformed payload rejected without stack trace or implementation details.
- Localhost, loopback, link-local, and private-address link previews do not trigger outbound access.
- Disallowed CORS origin receives no allow-origin header.
- TRACE and unsupported methods are rejected.
- Production Swagger and non-health actuator endpoints are unavailable.
- Required security headers are present.

## 6. Evidence model

The final report includes:

- `status`, `qualityGatePassed`, `evidenceClass=managed-local`, and `productionProven=false`.
- Git commit and dirty-worktree marker.
- Baseline versions and explicit non-claims.
- Static, dependency, container/configuration, and dynamic test results.
- Per-test status, expected/actual HTTP status, sanitized evidence, and failure reason.
- Scanner status, database age where available, vulnerability counts, SBOM hashes, and report hashes.
- Cleanup status proving disposable services and temporary credentials were removed.

No report may contain JWTs, passwords, CSRF tokens, API keys, cookies, reset tokens, raw note content, or complete user identifiers.

## 7. Acceptance criteria

The item passes only when:

1. JWT local-storage and JSON exposure is removed.
2. CSRF, exact CORS, cookie flags, session restoration, rate limit, and logout behavior pass automated tests.
3. Cross-tenant and admin authorization attacks fail closed.
4. Official npm audit has zero high/critical findings.
5. Backend SCA is available and has zero unaccepted high/critical findings.
6. Runtime images are non-root and configuration/header checks pass.
7. Isolated dynamic penetration report passes with cleanup verified.
8. Backend and frontend regression suites and production builds pass.
9. The compliance matrix records every scoped control as PASS, PARTIAL, NOT_APPLICABLE, or EXTERNAL with evidence and no unexplained status.

## 8. Explicit non-claims

Managed-local evidence does not prove internet-edge/WAF behavior, cloud IAM, KMS/HSM operation, DDoS resistance, host hardening, network segmentation, production TLS termination, third-party processor compliance, legal GDPR/PIPL conclusions, SOC 2/ISO 27001 certification, or an independent manual penetration test.
