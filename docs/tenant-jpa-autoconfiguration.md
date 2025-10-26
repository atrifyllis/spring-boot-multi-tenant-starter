# Tenant JPA Auto-Configuration

`TenantJpaAutoConfiguration` wires the tenant `DataSource`, JPA `EntityManagerFactory`, and
transaction manager that mirror the core application stack. The configuration now reuses Spring
Boot's `EntityManagerFactoryBuilder`, ensuring that:

* User supplied `spring.jpa.*`/`multitenancy.tenant.jpa.*` properties (dialect, naming strategy,
  `ddl-auto`, vendor options, etc.) are applied to the tenant persistence unit.
* `EntityManagerFactoryBuilderCustomizer` beans contributed by the consuming application can
  tweak the builder before it is materialised.
* The resulting `LocalContainerEntityManagerFactoryBean` keeps parity with Boot's default JPA
  configuration, including vendor adapters and any registered customisers.

Applications can continue to register their own `tenantEntityManagerFactory` bean when they need
to replace the defaults entirely, but the majority of tuning should now flow through standard
Spring Boot knobs instead of bespoke overrides.
