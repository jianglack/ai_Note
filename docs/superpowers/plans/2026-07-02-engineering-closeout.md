# Engineering Closeout Implementation Plan

> **For agentic workers:** Execute this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Review, verify, stage, and commit the completed audit-remediation work without hiding unverified gaps or generated artifacts.

**Architecture:** This is a closeout plan, not a feature plan. It treats the current `fix/audit-remediation` working tree as the implementation under review, validates the risk areas that changed, and commits only after fresh verification passes.

**Tech Stack:** Git, Maven/Spring Boot/JUnit/JaCoCo, npm/Vitest/Vite/Playwright, k6 scripts, PowerShell release scripts.

---

### Task 1: Review Commit Boundary

**Files:**
- Review: `git status --short`
- Review: `git diff --stat`
- Review: `git diff --name-status`
- Review: `git ls-files --others --exclude-standard`

- [x] **Step 1: Confirm branch and repository shape**

Run:

```powershell
git branch --show-current
git rev-parse --show-toplevel
git rev-parse --git-dir
git rev-parse --git-common-dir
```

Expected:
- Branch is `fix/audit-remediation`.
- Git dir and common dir are both `.git`, so this is a normal repo, not a removable worktree.

- [x] **Step 2: Confirm only intended files are candidates**

Run:

```powershell
git diff --name-status
git ls-files --others --exclude-standard
git diff --check
```

Expected:
- Modified files are source, tests, config, docs, perf scripts, CI, and `.gitignore`.
- Untracked files are new tests, docs, e2e, perf, or scripts.
- No `frontend/coverage`, `.codex-run`, backend target output, token files, or local reports are tracked.
- `git diff --check` exits 0.

### Task 2: Review High-Risk Diffs

**Files:**
- Review: `backend/src/main/java/com/ainote/app/controller/AiController.java`
- Review: `backend/src/main/java/com/ainote/app/service/AgentService.java`
- Review: `backend/src/main/java/com/ainote/app/service/chat/ReadOnlyStreamingChatService.java`
- Review: `backend/src/main/java/com/ainote/app/service/AgentCapacityLimiter.java`
- Review: `backend/src/main/java/com/ainote/app/entity/SemanticMemory.java`
- Review: `backend/src/main/java/com/ainote/app/repository/SemanticMemoryRepository.java`
- Review: `backend/src/test/java/com/ainote/app/RepositoryIT.java`
- Review: `perf/ainote-load.js`
- Review: `scripts/verify-release.ps1`
- Review: `frontend/vitest.config.ts`
- Review: `.github/workflows/ci.yml`

- [x] **Step 1: Inspect SSE and capacity changes**

Run:

```powershell
git diff -- backend/src/main/java/com/ainote/app/controller/AiController.java
git diff -- backend/src/main/java/com/ainote/app/service/AgentService.java
Get-Content -Raw backend/src/main/java/com/ainote/app/service/chat/ReadOnlyStreamingChatService.java
Get-Content -Raw backend/src/main/java/com/ainote/app/service/AgentCapacityLimiter.java
```

Expected:
- SSE events include heartbeat, retry, id, token, complete, error, and cancellation handling.
- Agent capacity rejection returns `AGENT_BUSY` and does not count as strict admitted-agent success.
- Direct-stream skips context assembly when `noteIds` is empty.
- Lightweight admitted-agent path still acquires AgentService capacity before calling the DeepSeek agent chat model.

- [x] **Step 2: Inspect database integration changes**

Run:

```powershell
git diff -- backend/src/main/java/com/ainote/app/entity/SemanticMemory.java backend/src/main/java/com/ainote/app/repository/SemanticMemoryRepository.java backend/src/main/java/com/ainote/app/service/MemoryExtractionService.java
Get-Content -Raw backend/src/test/java/com/ainote/app/TestDatabaseProperties.java
Get-Content -Raw backend/src/test/java/com/ainote/app/RepositoryIT.java
```

Expected:
- `SemanticMemory.embedding` is not written through broken JPA vector binding.
- Embedding persistence uses native `cast(:embedding AS vector)`.
- Temporary database mode requires `AINOTE_IT_TEMP_DB=true`.
- RepositoryIT covers NoteConcept, SemanticMemory pgvector update/search, EpisodicMemory, and notification read state.

- [x] **Step 3: Inspect frontend and release gates**

Run:

```powershell
git diff -- frontend/vitest.config.ts frontend/package.json .github/workflows/ci.yml .gitignore
Get-Content -Raw perf/ainote-load.js
Get-Content -Raw scripts/verify-release.ps1
```

Expected:
- CI uses `npm run test:coverage`.
- Vitest thresholds are explicit: statements 48, branches 38, functions 50, lines 50.
- k6 `sse_100` can distinguish direct-stream, `AGENT_BUSY`, same-user guard, and admitted-agent completions.
- Release wrapper fails on skipped Maven tests.

### Task 3: Run Final Verification

**Files:**
- Verify: `backend/target/surefire-reports`
- Verify: `backend/target/failsafe-reports`
- Verify: `backend/target/site/jacoco/jacoco.xml`
- Verify: `frontend/coverage/lcov.info`

- [x] **Step 1: Run backend full verification against a temporary pgvector database**

Run:

```powershell
$env:JWT_SECRET='codex_test_jwt_secret_20260702_abcdefghijklmnopqrstuvwxyz1234567890'
$env:POSTGRES_PASSWORD='ainote_dev_password'
$env:AINOTE_IT_TEMP_DB='true'
$env:AINOTE_IT_JDBC_ADMIN_URL='jdbc:postgresql://127.0.0.1:5432/postgres'
$env:AINOTE_IT_JDBC_USERNAME='ainote'
$env:AINOTE_IT_JDBC_PASSWORD='ainote_dev_password'
mvn -q clean verify
```

Expected:
- Exit code 0.
- Maven XML reports sum to `771 tests, 0 failures, 0 errors, 0 skipped`.
- JaCoCo package gates pass.

- [x] **Step 2: Run frontend coverage and build**

Run:

```powershell
npm run test:coverage
npm run build
```

Expected:
- `21` Vitest files pass with `58` tests.
- Coverage meets configured thresholds.
- Vite build exits 0.

- [x] **Step 3: Run safety scans**

Run broad scans for committed key patterns, environment-variable assignments containing provider keys, the known user-supplied key fingerprint, and test-style blockers. The exact key and fingerprint strings are intentionally not stored in this committed plan.

```powershell
rg -n "<provider-key-pattern>" . -g "!frontend/node_modules/**" -g "!frontend/coverage/**" -g "!frontend/dist/**" -g "!backend/target/**" -g "!target/**" -g "!.codex-run/**"
rg -n "<provider-env-assignment-pattern>" . -g "!frontend/node_modules/**" -g "!frontend/coverage/**" -g "!frontend/dist/**" -g "!backend/target/**" -g "!target/**" -g "!.codex-run/**"
rg -n "<known-key-fingerprint>" . -g "!frontend/node_modules/**" -g "!frontend/coverage/**" -g "!frontend/dist/**" -g "!backend/target/**" -g "!target/**" -g "!.codex-run/**"
rg -n "@ExtendWith\(MockitoExtension\.class\)|Map\.of\([^\n]*null|test\.only|describe\.only|it\.only|fit\(" backend/src/test frontend/tests frontend/e2e
```

Expected:
- Secret scans return no matches.
- Test style scan returns no matches.

### Task 4: Stage and Commit

**Files:**
- Stage: all reviewed source, tests, docs, scripts, perf, CI, and `.gitignore` changes.
- Exclude: generated reports, local token files, coverage output, target/dist output, and local-only secret reports.

- [ ] **Step 1: Stage reviewed changes**

Run:

```powershell
git add .github .gitignore backend frontend docs perf scripts
```

Expected:
- `git status --short` shows staged source/test/doc/script changes.
- No `frontend/coverage`, `.codex-run`, backend `target`, or secret report files are staged.

- [ ] **Step 2: Inspect staged diff**

Run:

```powershell
git diff --cached --stat
git diff --cached --check
git status --short
```

Expected:
- Staged stat matches the reviewed implementation.
- `git diff --cached --check` exits 0.
- No unintended generated artifacts are staged.

- [ ] **Step 3: Commit**

Run:

```powershell
git commit -m "test: close audit coverage and release gates"
```

Expected:
- Commit succeeds on branch `fix/audit-remediation`.
- Working tree is clean or contains only intentionally ignored/generated files.

### Task 5: Integration Decision

**Files:**
- Review: current branch and remotes.

- [ ] **Step 1: Stop before merge or push unless explicitly requested**

Run:

```powershell
git branch --show-current
git log -1 --oneline
```

Expected:
- Report the commit hash and verification evidence.
- Do not merge into `main` or push to `origin` without explicit user instruction.

---

## Plan Self-Review

- Spec coverage: The plan covers review, plan audit, final verification, stage, commit, and integration boundary.
- Placeholder scan: No deferred-work markers or unspecified commands are present.
- Command boundary: Every destructive or irreversible action is limited to `git commit`; merge, push, and discard are explicitly excluded.
- Safety boundary: Generated outputs and local secrets are excluded by scan and `.gitignore` checks.
