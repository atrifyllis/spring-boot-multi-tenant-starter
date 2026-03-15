# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew build                # Full build with tests
./gradlew clean build          # Clean build
./gradlew test                 # Run all tests
./gradlew test --tests "com.github.atrifyllis.multitenancy.SomeTest"  # Run a single test class
./gradlew publishToMavenLocal  # Publish to local Maven (for local consumer testing)
```

Tests require Docker (Testcontainers spins up PostgreSQL 16-alpine).

## Architecture

This is a **Spring Boot auto-configuration starter library** that adds PostgreSQL Row-Level Security (RLS)-based multi-tenancy to consumer applications. It is not an application itself.

### Tenant Isolation Flow

```
HTTP Request
  → TenantInterceptor      (extracts tenantId UUID from JWT claim)
  → TenantContext          (InheritableThreadLocal<UUID>)
  → TenantAwareDataSource  (proxy: SET app.tenant_id = '<uuid>' on each connection)
  → PostgreSQL RLS policy  (tenant_id = current_setting('app.tenant_id')::uuid)
```

### Source Layout

```
src/main/kotlin/com/github/atrifyllis/multitenancy/
├── autoconfigure/          # Spring Boot @AutoConfiguration classes + @ConfigurationProperties
├── application/service/    # TenantContext (thread-local) + TenantInterceptor (web)
└── adapters/secondary/persistence/  # TenantAwareDataSource, TenantListener, TenantAware
```

Auto-configuration entry points are registered in:
`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### Two DataSource Model

| Bean | DB User | Purpose |
|------|---------|---------|
| `adminDataSource` | `postgres`/owner | Flyway migrations, jOOQ admin queries — bypasses RLS |
| `tenantDataSource` | `app_user` | All JPA operations — RLS enforced via `TenantAwareDataSource` wrapper |

`TenantAwareDataSource` intercepts `getConnection()` via Java reflection proxy and executes `SET app.tenant_id` / `RESET app.tenant_id` around each connection lifecycle.

### RLS Setup

`src/main/resources/db/migration/postgresql/R__enforce_rls.sql` is a repeatable Flyway migration (re-run via timestamp placeholder injected by `RlsAutoConfiguration`) that:
- Iterates all tables in the configured schema
- Enables RLS and creates `tenant_isolation_policy` for each non-excluded table
- Policy condition: `tenant_id = current_setting('app.tenant_id')::uuid`

### JPA Entity Integration

Entities implement the `TenantAware` interface (single method `setTenantId(UUID?)`). The `TenantListener` JPA entity listener (@PrePersist/@PreUpdate/@PreRemove) automatically sets `tenant_id` from `TenantContext` before database operations.

## Testing

`BasePostgresTest` is the shared base for integration tests:
- Starts a reusable Testcontainers PostgreSQL 16-alpine instance
- Disables Spring Boot's auto-Flyway (tests run migrations manually via `migrate()`)
- Provides `adminExec(sql)` helper to run SQL bypassing RLS (uses admin datasource)

## Key Configuration Properties

Consumer applications configure the starter via `application.properties`:

```properties
# Required
multitenancy.datasource.url=jdbc:postgresql://...
multitenancy.datasource.admin-username=postgres
multitenancy.datasource.admin-password=...
multitenancy.datasource.app-username=app_user
multitenancy.datasource.app-password=...

# Optional
multitenancy.rls.schema=public                  # default: public
multitenancy.rls.excluded-tables=flyway_schema_history  # comma-separated
```

## Known Security Issues

| Severity | Issue | Location |
|----------|-------|----------|
| CRITICAL | Connection leak — proxy `close()` swallows RESET exceptions, may leak connections or return them with stale tenant context | `TenantAwareDataSource.kt:56-60` |
| HIGH | Silent null tenant — `@PrePersist`/`@PreUpdate` sets `tenant_id` to null when `TenantContext` has no tenant, allowing entities without tenant isolation | `TenantListener.kt:12-14` |
| MEDIUM | Empty string validation missing on RLS config properties (`rls.schema`, `rls.tenantColumn`, `rls.policyName`), which would produce broken SQL | `MultitenancyProperties.kt` |
| MEDIUM | Unhandled `UUID.fromString()` — malformed JWT tenant claim causes unhandled 500 | `TenantInterceptor.kt` |
| MEDIUM | `InheritableThreadLocal` tenant leak with thread pool reuse (`@Async`, virtual threads) | `TenantContext.kt` |
| LOW | String interpolation in SET SQL (mitigated by UUID type validation) | `TenantAwareDataSource.kt` |

## Distribution

Published via JitPack. Version is `0.0.1-SNAPSHOT`. `jitpack.yml` configures the JitPack build (Java 21, runs `publishToMavenLocal`).
