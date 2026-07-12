# Security Penetration and Compliance Implementation Plan

## Objective

Close confirmed application and supply-chain security gaps, then produce repeatable managed-local security evidence without touching existing user data.

## Execution sequence

1. Record the threat model and baseline mapping to OWASP ASVS 5.0.0, OWASP API Security 2023, NIST SSDF 1.1, and NIST SP 800-63B-4.
2. Migrate browser authentication from bearer JWT in `localStorage` to an HttpOnly cookie with CSRF protection and authenticated session bootstrap.
3. Replace blanket public auth routing with exact public endpoint rules and exact-origin CORS validation.
4. Raise new/reset password minimum length to 15 code points and update frontend validation.
5. Upgrade vulnerable frontend dependencies until official-registry npm audit has no high/critical findings.
6. Harden Nginx and both runtime containers; add configuration tests.
7. Add backend/frontend SBOM generation, dependency gates, caching, and scanner-unavailable handling to CI.
8. Implement a disposable dynamic API penetration runner with redacted machine-readable evidence.
9. Add security asset tests and a versioned compliance matrix.
10. Run targeted tests, SCA, isolated dynamic tests, full regressions, container builds, and final review.

## Safety controls

- Dynamic tests use generated `ainote-security-*` container names only.
- Existing `ainote-postgres`, `ainote-redis`, `ainote-neo4j`, backend, and frontend processes are read-only health checks.
- Test users use `.invalid` addresses and generated credentials that are never written to final reports.
- Destructive BOLA tests target only disposable fixtures.
- Every runner path has unconditional process/container cleanup.
- Dependency upgrades are lockfile-controlled and regression-tested.

## Stop conditions

Stop and fail the gate for authentication regression, token exposure, CSRF bypass, cross-tenant access, admin bypass, scanner unavailable, high/critical vulnerability, root runtime image, missing SBOM, unexpected outbound SSRF, cleanup failure, or evidence containing a secret.

## Rollback

Authentication contract changes are atomic across backend, frontend, and tests. Do not roll back only the CSRF or frontend half. If rollback is required, revert the complete task change set and retain the failed evidence report for analysis.
