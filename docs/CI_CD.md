# CI/CD

## Current CI

`/.github/workflows/ci.yml` runs on every push and pull request.

- `gitleaks`: scans committed content for secrets.
- `backend`: runs Java 17 `mvn -B verify`.
- `frontend`: runs Node 24 `npm ci`, TypeScript build checks, production build, lint/security tests, and Vitest coverage gates.
- `dependency security and SBOM`: generates backend and frontend CycloneDX SBOMs, runs official-registry npm audit, verifies the OSV Scanner 2.4.0 checksum, applies the time-bounded exception policy, and uploads evidence even when the gate fails.

This workflow is the merge gate for normal development. Scanner unavailability, checksum mismatch, an unaccepted vulnerability, or a missing evidence artifact fails the gate.

## Current CD

`/.github/workflows/release.yml` is continuous delivery, not automatic production deployment.

It runs on:

- pushes to `main`
- tags matching `v*`
- manual `workflow_dispatch`

It produces:

- backend jar artifact
- frontend `dist` artifact
- release manifest artifact containing repository, ref, sha, run id, and artifact names

## What It Does Not Do

The release workflow does not deploy to production, connect to servers, push Docker images, or use production credentials.

Production deployment should be added only after these are defined:

- target staging and production hosts
- secret storage and rotation rules
- database migration strategy
- deployment approval rules
- rollback procedure
- smoke tests after deployment

## Recommended Path

Use CI for PR quality gates. Use release artifacts for manual or staging releases. Add automated staging deployment before adding any production deployment.
