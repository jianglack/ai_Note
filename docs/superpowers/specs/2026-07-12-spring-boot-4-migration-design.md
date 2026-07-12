# Spring Boot 4 migration design

Date: 2026-07-12
Status: planned, not approved for merge

## Objective

Move AiNote from Spring Boot 3.5.x to Spring Boot 4 without mixing the major migration with routine dependency updates, while preserving security overrides, memory-system behavior, database compatibility, observability, and release reproducibility.

## Non-goals

- Do not merge the existing Dependabot Spring Boot 4 commit.
- Do not downgrade JaCoCo or remove security overrides to make the build green.
- Do not combine frontend dependency upgrades or unrelated refactors with this migration.
- Do not claim completion from compilation alone.

## Migration strategy

### Phase 0: stabilize the 3.5 baseline

1. Keep main on the latest tested Spring Boot 3.5 maintenance release.
2. Remove or replace Boot 3 deprecations while still on 3.5, especially `@MockBean`.
3. Require the normal main quality gates to stay green after each compatibility-only commit.

Exit gate: no Boot APIs known to be removed in Boot 4 remain in production or test code.

### Phase 1: create an isolated migration branch

1. Branch from the then-current `main` using `codex/spring-boot-4-migration`.
2. Upgrade to the selected supported Boot 4 maintenance line, not an unreviewed grouped Dependabot commit.
3. Upgrade springdoc separately within the same migration branch because its major version is tied to Boot 4.
4. Keep JaCoCo 0.8.15 and all explicit security constraints.

Exit gate: dependency model resolves from a clean Maven repository and produces a reviewable dependency tree.

### Phase 2: dependency and module migration

1. Rename the Jackson 2 override to `jackson-2-bom.version` as a temporary compatibility bridge.
2. Explicitly choose the Jackson strategy:
   - short-term: retain Jackson 2 using Boot's compatibility module;
   - target: migrate imports and behavior to Jackson 3 in a separately reviewed step.
3. Replace deprecated classic starter coordinates with Boot 4 module-specific starters where practical.
4. Add the Web MVC test starter required by Boot 4 modularization.
5. Generate and compare dependency trees, SBOMs, and vulnerability reports against the 3.5 baseline.

Exit gate: Maven dependency convergence, SCA, and SBOM gates pass with no unexplained regression.

### Phase 3: source compatibility

1. Construct `DaoAuthenticationProvider` with `UserDetailsService`.
2. Replace all 23 `@MockBean` usages with Spring Framework's `@MockitoBean` and update imports.
3. Move the 9 Web MVC test imports to the Boot 4 packages/modules.
4. Update Spring 7 exception-constructor tests using real minimal request/input objects.
5. Run the properties migrator temporarily and review every renamed or removed property; remove the migrator before merge.

Exit gate: main and test source compilation pass without suppressed migration errors.

### Phase 4: behavioral validation

Run the complete existing validation set, not a reduced smoke suite:

- `mvn -B verify`
- PostgreSQL and Testcontainers integration tests
- Flyway migration and clean-database bootstrap
- authentication, JWT, CORS, CSRF, and admin authorization tests
- memory capture, retrieval, privacy, deletion, replay, and advisor gates
- short-term memory concurrency and reliability tests
- actuator, Prometheus, logging, and health endpoint checks
- springdoc/OpenAPI endpoint validation
- packaged JAR startup and graceful shutdown

Exit gate: zero failed tests and no unexplained behavioral delta from the 3.5 baseline.

### Phase 5: release and rollback proof

1. Build the exact release artifact in GitHub Actions.
2. Restore a representative database backup into the test environment and run the new artifact.
3. Verify rollback to the last 3.5 artifact without database incompatibility.
4. Record startup time, memory use, endpoint p95, and error-rate deltas.

Exit gate: release artifacts, SBOMs, rollback evidence, and review sign-off are attached to the migration pull request.

## Required quality gates

The migration may merge only when all are true:

- clean Maven dependency resolution
- full backend verify passes
- frontend CI remains unaffected and passes
- SCA and secret scans pass
- no JaCoCo downgrade or coverage-gate relaxation
- PostgreSQL integration tests pass
- security and privacy regression tests pass
- packaged application starts with the production profile contract
- rollback procedure is tested
- migration review contains no unresolved high-severity finding

## Rollback policy

Before merge, rollback is branch deletion or commit reversion with no main impact. After merge, the release must retain the last Boot 3.5 artifact and prohibit irreversible database migrations until the Boot 4 observation window completes.

## Current disposition

The migration is not ready for implementation on `main`. The compatibility audit has identified concrete POM, Spring Security, test module, mocking annotation, and Spring Framework constructor changes. These must be delivered as small reviewable commits on the dedicated migration branch, followed by the complete quality-gate sequence above.
