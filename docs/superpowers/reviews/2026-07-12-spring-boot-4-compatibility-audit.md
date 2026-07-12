# Spring Boot 4 compatibility audit

Date: 2026-07-12

## Decision

The Dependabot Spring Boot 4 pull request must not be merged in its current form. The upgrade is a major-version migration with confirmed build breakages, and its commit is based on an older main branch that also reverts JaCoCo 0.8.15 to 0.8.11.

Current production baseline remains Spring Boot 3.5.16 on Java 17.

## Reproduction scope

The audit was run in the isolated worktree `D:\ainotetest-worktrees\spring-boot-4-migration-audit`. No migration changes were applied to `main`.

The Dependabot version changes were replayed without the JaCoCo downgrade:

- Spring Boot 3.5.16 to 4.1.0
- springdoc-openapi 2.8.17 to 3.0.3
- JaCoCo retained at 0.8.15

For source-compatibility diagnosis, Spring Boot 4.0.7 was also used as the 4.0 maintenance baseline.

## Confirmed findings

### 1. Jackson BOM property collision

The project explicitly sets `jackson-bom.version` to 2.21.5. In Spring Boot 4 this property controls the `tools.jackson` Jackson 3 BOM, while the Jackson 2 compatibility property is `jackson-2-bom.version`.

Observed failure:

```text
Non-resolvable import POM: tools.jackson:jackson-bom:pom:2.21.5
```

Changing only the property name to `jackson-2-bom.version` allowed Maven to resolve the dependency model. This is a project migration issue, not an unavailable Spring Boot release.

### 2. Spring Security 7 API break

`DaoAuthenticationProvider` no longer supports the project's no-argument construction plus `setUserDetailsService` call.

Observed compile errors:

```text
DaoAuthenticationProvider requires UserDetailsService
setUserDetailsService(...) cannot be found
```

Using `new DaoAuthenticationProvider(userDetailsService)` allowed all 333 main Java source files to compile under the 4.0.7 diagnostic baseline.

### 3. Test infrastructure is not Boot 4 compatible

Test compilation failed after main compilation succeeded:

- 9 Web MVC test files import the removed Boot 3 test package.
- 23 `@MockBean` usages remain across those files.
- Boot 4 modularization requires the Web MVC test starter/module explicitly.
- Spring's replacement is `org.springframework.test.context.bean.override.mockito.MockitoBean`.

### 4. Spring Framework 7 constructor changes

`GlobalExceptionHandlerTest` uses constructors that no longer exist:

- `NoResourceFoundException(HttpMethod, String)` now requires the resource path argument.
- `HttpMessageNotReadableException(String)` now requires an `HttpInputMessage`.

### 5. Dependabot branch regression

The Dependabot commit changes JaCoCo 0.8.15 back to 0.8.11 because it was generated from an older base. It must be rebased or replaced by a purpose-built migration branch.

## Evidence

| Gate | Result |
| --- | --- |
| Spring Boot 4.1.0 dependency model with original project property | Failed |
| Spring Boot 4.0.7 dependency model after Jackson property rename | Passed |
| Main source compilation after Security 7 constructor update | Passed, 333 files |
| Test source compilation | Failed, confirmed migration work remains |
| Full unit/integration test suite | Not reached |
| OWASP/SBOM quality gate | Not reached on corrected migration baseline |

## Review conclusion

The failure is a combination of project compatibility issues and a stale Dependabot branch, not a reason to patch the current main branch opportunistically. Spring Boot 4 must remain isolated until every gate in the migration design passes.

Primary references:

- https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide
- https://docs.spring.io/spring-boot/
