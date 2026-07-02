# Release Artifacts CD Implementation Plan

> **For agentic workers:** Execute this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a conservative continuous-delivery workflow that builds verified release artifacts without automatically deploying to production.

**Architecture:** Keep the existing `ci.yml` as the PR/push quality gate. Add a separate `release.yml` that runs on `main`, version tags, or manual dispatch, rebuilds backend/frontend outputs from a clean runner, and uploads immutable GitHub Actions artifacts. Production deployment remains manual until staging/production targets, secrets, approvals, and rollback rules are explicitly defined.

**Tech Stack:** GitHub Actions, Maven/Spring Boot, Node/npm/Vite, actions/upload-artifact.

---

### Task 1: Add Release Artifact Workflow

**Files:**
- Create: `.github/workflows/release.yml`

- [ ] **Step 1: Create the workflow**

Create `.github/workflows/release.yml` with:

```yaml
name: Release Artifacts

on:
  workflow_dispatch:
  push:
    branches:
      - main
    tags:
      - "v*"

permissions:
  contents: read

concurrency:
  group: release-artifacts-${{ github.ref }}
  cancel-in-progress: true

jobs:
  backend-artifact:
    name: backend artifact
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
          cache: maven
      - name: Verify and package backend
        working-directory: backend
        run: mvn -B verify
      - name: Upload backend jar
        uses: actions/upload-artifact@v4
        with:
          name: ainote-backend-${{ github.run_number }}
          path: backend/target/*.jar
          if-no-files-found: error
          retention-days: 30

  frontend-artifact:
    name: frontend artifact
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: "20"
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - name: Install
        working-directory: frontend
        run: npm ci
      - name: Typecheck
        working-directory: frontend
        run: npx tsc -b
      - name: Build
        working-directory: frontend
        run: npm run build
      - name: Coverage gate
        working-directory: frontend
        run: npm run test:coverage
      - name: Upload frontend dist
        uses: actions/upload-artifact@v4
        with:
          name: ainote-frontend-${{ github.run_number }}
          path: frontend/dist/**
          if-no-files-found: error
          retention-days: 30

  release-manifest:
    name: release manifest
    runs-on: ubuntu-latest
    needs:
      - backend-artifact
      - frontend-artifact
    steps:
      - name: Write release manifest
        shell: bash
        run: |
          {
            echo "repository=${GITHUB_REPOSITORY}"
            echo "ref=${GITHUB_REF}"
            echo "sha=${GITHUB_SHA}"
            echo "run_id=${GITHUB_RUN_ID}"
            echo "run_number=${GITHUB_RUN_NUMBER}"
            echo "backend_artifact=ainote-backend-${GITHUB_RUN_NUMBER}"
            echo "frontend_artifact=ainote-frontend-${GITHUB_RUN_NUMBER}"
          } > release-manifest.txt
      - name: Upload release manifest
        uses: actions/upload-artifact@v4
        with:
          name: ainote-release-manifest-${{ github.run_number }}
          path: release-manifest.txt
          if-no-files-found: error
          retention-days: 30
```

- [ ] **Step 2: Verify workflow syntax**

Run:

```powershell
docker run --rm -v "${PWD}:/repo" -w /repo rhysd/actionlint:latest -color
```

Expected:
- Exit code 0.
- No actionlint errors.

### Task 2: Document CI/CD Boundaries

**Files:**
- Create: `docs/CI_CD.md`

- [ ] **Step 1: Create documentation**

Create `docs/CI_CD.md` explaining:

```markdown
# CI/CD

## Current CI

`/.github/workflows/ci.yml` runs on every push and pull request.

- `gitleaks`: scans committed content for secrets.
- `backend`: runs Java 17 `mvn -B verify`.
- `frontend`: runs Node 20 `npm ci`, TypeScript build checks, production build, lint/security tests, and Vitest coverage gates.

This workflow is the merge gate for normal development.

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
```

- [ ] **Step 2: Validate documentation is tracked**

Run:

```powershell
git status --short
```

Expected:
- `.github/workflows/release.yml` is untracked or staged.
- `docs/CI_CD.md` is untracked or staged.

### Task 3: Verify and Commit

**Files:**
- Verify: `.github/workflows/release.yml`
- Verify: `docs/CI_CD.md`

- [ ] **Step 1: Run final checks**

Run:

```powershell
git diff --check
git status --short
```

Expected:
- `git diff --check` exits 0.
- Only the release workflow, CI/CD docs, and this plan are modified.

- [ ] **Step 2: Commit**

Run:

```powershell
git add .github/workflows/release.yml docs/CI_CD.md docs/superpowers/plans/2026-07-03-release-artifacts-cd.md
git commit -m "ci: add release artifact delivery workflow"
git push
```

Expected:
- Commit succeeds.
- Push updates `origin/fix/audit-remediation`.
