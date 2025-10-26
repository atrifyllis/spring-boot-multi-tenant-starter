# Codebase Review Findings

## Critical issues

### Tenant context leaks to connections without a tenant
`TenantAwareDataSource#setTenantId` blindly executes `SET app.tenant_id TO '$tenantId'` even when the `TenantContext` is empty, which turns into `SET ... TO 'null'`. Downstream, the RLS policy casts the setting to `uuid`, so any request that reaches the datasource without a tenant (e.g. unauthenticated endpoint or background job) fails with `invalid input syntax for type uuid: "null"`. Skip the statement when the context is absent (or `RESET` the setting) so metadata queries and anonymous calls do not break. 【F:src/main/kotlin/io/github/atrifyllis/multitenancy/adapters/secondary/persistence/TenantAwareDataSource.kt†L31-L38】

### Tenant context can bleed between requests when a controller throws
The interceptor clears the context in `postHandle`, but Spring skips that callback whenever the handler raises an exception. In those scenarios the tenant ID remains on the thread and the next request can inherit it. Move the cleanup to `afterCompletion`, or guard `TenantContext.setTenantId` in a try/finally. 【F:src/main/kotlin/io/github/atrifyllis/multitenancy/application/service/TenantInterceptor.kt†L24-L44】

### Admin entity scanning is hard-coded
`AdminDataSourceAutoConfiguration` still scans `io.ktri.expense.tracker.admin`, so any consumer that keeps their admin entities elsewhere silently fails to register them. Expose the package(s) as a property or drop the scan from the starter so applications can configure it themselves. 【F:src/main/kotlin/io/github/atrifyllis/multitenancy/autoconfigure/AdminDataSourceAutoConfiguration.kt†L100-L113】

## Security concerns

### Sensitive JWT data is logged at INFO level
`TenantInterceptor` prints the full JWT and the raw `tenantId` claim at INFO, which leaks credentials and PII into application logs. Remove the token body (or log only the token ID) and demote the message to DEBUG. 【F:src/main/kotlin/io/github/atrifyllis/multitenancy/application/service/TenantInterceptor.kt†L24-L31】

### Conflicting tenant ID yields 500s instead of client-friendly responses
When the `X-Tenant-ID` header does not match the JWT, the interceptor throws `TenantIdsDoNotMatchException`. Without an exception handler this becomes a 500. Convert the mismatch into an HTTP 403 (or configurable status) so clients receive a useful error. 【F:src/main/kotlin/io/github/atrifyllis/multitenancy/application/service/TenantInterceptor.kt†L46-L53】

## Reliability & correctness

### `TenantListener` should fail fast on missing context
`TenantListener` writes whatever `TenantContext.getTenantId()` returns, including `null`. Persisting without a tenant silently breaks isolation. Throw when the context is empty so bugs surface early. 【F:src/main/kotlin/io/github/atrifyllis/multitenancy/adapters/secondary/persistence/TenantListener.kt†L8-L15】

### Repeatable RLS migration reruns on every startup
`RlsAutoConfiguration` injects a `migration_timestamp` placeholder with `System.currentTimeMillis()`, changing on each boot and forcing Flyway to reapply the repeatable migration even when nothing changed. Hash the relevant properties instead so reruns happen only when the configuration actually changes. 【F:src/main/kotlin/io/github/atrifyllis/multitenancy/autoconfigure/RlsAutoConfiguration.kt†L26-L40】

## Performance & maintainability

### Connection proxy generation is expensive
`TenantAwareDataSource#getTenantAwareConnectionProxy` builds and loads a fresh ByteBuddy subclass for every connection acquisition. This is costly under load and can cause classloader churn. Cache the generated proxy class or switch to JDK dynamic proxies to reuse the type. 【F:src/main/kotlin/io/github/atrifyllis/multitenancy/adapters/secondary/persistence/TenantAwareDataSource.kt†L46-L67】

### Avoid inheritable thread locals for tenant context
`TenantContext` uses `InheritableThreadLocal`, so worker pools that spawn child threads (e.g. `@Async`) inherit parent tenant IDs unexpectedly. A plain `ThreadLocal` or a request-scoped bean avoids that bleed-through. 【F:src/main/kotlin/io/github/atrifyllis/multitenancy/application/service/TenantContext.kt†L6-L19】

## Additional improvement ideas

* Offer hooks (properties or customisers) to tweak the admin entity manager similarly to the tenant side, so consumers can supply vendor options without replacing the bean. 【F:src/main/kotlin/io/github/atrifyllis/multitenancy/autoconfigure/AdminDataSourceAutoConfiguration.kt†L100-L134】
* Validate `multitenancy.tenant-jpa-base-package` eagerly and surface a helpful error when it is missing or empty, preventing puzzling runtime failures. 【F:src/main/kotlin/io/github/atrifyllis/multitenancy/autoconfigure/MultitenancyProperties.kt†L7-L13】
