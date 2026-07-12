# Security Penetration and Compliance Pre-implementation Review

## Decision

Approved to implement after confirming the findings below. The current state must not be described as passing the third production-readiness item.

## Confirmed findings

1. **HIGH - Browser token exposure**: login/register return JWT in JSON and the frontend persists it in `localStorage`. Any successful script injection can exfiltrate the long-lived bearer token even though an HttpOnly cookie is also set.
2. **HIGH - Incomplete CSRF contract**: cookie authentication exists while Spring CSRF is globally disabled. `SameSite=Lax` reduces common cross-site requests but is not the complete application control required for cookie-authenticated unsafe methods.
3. **HIGH - Vulnerable frontend dependencies**: official npm audit reported 7 high and 4 moderate vulnerable dependency groups, including direct Axios, React Router, and Vite findings with fixes available.
4. **HIGH - Backend SCA unavailable**: OWASP Dependency-Check 12.2.2 exceeded the 15-minute initial NVD synchronization limit without an API key. This is `UNAVAILABLE`, not a pass.
5. **MEDIUM - Password policy**: registration and reset accept six-character passwords. Current NIST single-factor guidance requires a minimum of 15 characters.
6. **MEDIUM - Public route breadth**: `/api/auth/**` is permitted wholesale, making future authenticated auth endpoints easy to expose accidentally.
7. **MEDIUM - CORS validation**: configuration tests avoid `*`, but startup code does not reject wildcard or malformed origin patterns.
8. **MEDIUM - Runtime container privilege**: both Docker runtime images omit `USER`, so backend and Nginx run as root by default.
9. **MEDIUM - Frontend response headers**: Nginx lacks explicit CSP, anti-framing, MIME, referrer, and permissions policy headers.
10. **PROCESS - Missing consolidated evidence**: security tests exist across many modules, but there is no single penetration report, SBOM evidence, vulnerability status, or versioned compliance matrix.

## Existing controls retained

- JWT issuer/signature/expiry validation and Redis token allow-list.
- BCrypt password hashing.
- Authentication attempt rate limiting and generic login failure.
- Tenant-scoped repository/service access patterns and admin allow-list audit.
- Generic exception responses without stack traces.
- Request-size validation, upload limits, prompt-injection guardrails, and tool execution governance.
- SSRF public-address validation with fixed-address socket fetching and TLS hostname verification.
- Production Swagger disablement and health-only actuator exposure.
- Gitleaks CI secret scanning.

## Required implementation review points

- Cookie-only migration must preserve SSE and API-client bearer support.
- CSRF implementation must cover login, registration, logout, and all unsafe browser requests without exposing the JWT.
- Dependency upgrades must be driven by official registry output and tested, not blindly forced.
- The dynamic runner must verify BOLA with two users and must not run against the main backend.
- Scanner failure and missing vulnerability database must remain blocking states.
- Final documentation must use `managed-local` and `productionProven=false`.
