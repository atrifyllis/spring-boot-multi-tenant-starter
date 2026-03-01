package com.github.atrifyllis.multitenancy

import com.github.atrifyllis.multitenancy.application.service.TenantContext
import java.time.Duration
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base class for all integration tests. Provides a shared PostgreSQL container and
 * Spring Boot context with the starter's auto-configuration active.
 *
 * ## Database users
 *
 * The container is initialized with two database roles (see `init.sql`):
 * - **`core`** (container owner) — used by `adminDataSource`. As the table owner, this role
 *   **bypasses RLS**, which is why admin operations (DDL, cross-tenant inserts) use it.
 * - **`app_user`** — used by `tenantDataSource`. This is a non-owner role, so PostgreSQL
 *   RLS policies are enforced on it, matching the production setup.
 *
 * ## Property wiring
 *
 * [initialize] sets Spring configuration properties pointing to the testcontainer. The starter's
 * auto-configurations ([AdminDataSourceAutoConfiguration], [TenantJpaAutoConfiguration]) read
 * these properties to create the `adminDataSource` and `tenantDataSource` beans, which are
 * then autowired back into this class and subclasses.
 *
 * ## Flyway
 *
 * Spring-managed Flyway is disabled (`spring.flyway.enabled=false`) because RLS tests need
 * to control migration timing: they create tables first, then call [migrate] to apply RLS
 * policies, and finally verify the policies work. The [migrate] method runs Flyway
 * programmatically with the [flywayCustomizer] from [RlsAutoConfiguration] (which injects
 * RLS-related placeholders).
 *
 * ## Nullable fields
 *
 * Both [adminDataSource] and [flywayCustomizer] are `required = false` because
 * [TenantInterceptorDisabledTest] sets `multitenancy.enabled=false`, which prevents the
 * auto-configurations from creating these beans.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [TestSecurityConfig::class],
)
@Testcontainers
@AutoConfigureMockMvc
open class BasePostgresTest {

    @Autowired(required = false)
    @Qualifier("adminDataSource")
    var adminDataSource: DataSource? = null

    @Autowired(required = false)
    var flywayCustomizer: FlywayConfigurationCustomizer? = null

    companion object {
        private const val APP_USER = "app_user"
        private const val APP_PASSWORD = "app_user"

        // init.sql creates the `app_user` role before Spring starts, so the tenant
        // datasource connection pool can connect as `app_user` immediately.
        val postgresContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16-alpine")
                .withTmpFs(mapOf("/var/lib/postgresql/data" to "rw"))
                .withUsername("core")
                .withPassword("core")
                .withCommand("postgres", "-c", "fsync=off", "-c", "log_statement=all")
                .withInitScript("init.sql")
                .withStartupTimeout(Duration.ofSeconds(90))
                .withReuse(true)

        init {
            postgresContainer.start()
        }

        /**
         * Feeds testcontainer connection details into Spring's Environment so the starter's
         * auto-configurations can create the datasource beans.
         */
        @JvmStatic
        @DynamicPropertySource
        fun initialize(registry: DynamicPropertyRegistry) {
            registry.add("multitenancy.admin.datasource.url", postgresContainer::getJdbcUrl)
            registry.add("multitenancy.tenant.datasource.url", postgresContainer::getJdbcUrl)

            // Admin connects as "core" (table owner, bypasses RLS)
            registry.add("multitenancy.admin.datasource.username", postgresContainer::getUsername)
            registry.add("multitenancy.admin.datasource.password", postgresContainer::getPassword)
            // Tenant connects as "app_user" (non-owner, RLS enforced)
            registry.add("multitenancy.tenant.datasource.username") { APP_USER }
            registry.add("multitenancy.tenant.datasource.password") { APP_PASSWORD }

            registry.add("multitenancy.tenant-jpa-base-package") {
                "com.github.atrifyllis.multitenancy"
            }
            registry.add("multitenancy.admin-jpa-packages-to-scan") {
                "com.github.atrifyllis.multitenancy.admin"
            }

            registry.add("spring.flyway.enabled") { "false" }
        }
    }

    @AfterEach
    fun clearTenantContext() {
        TenantContext.clear()
    }

    /** Executes raw SQL via the admin datasource (bypasses RLS). */
    protected fun adminExec(sql: String) {
        val ds = adminDataSource ?: error("adminDataSource is required to execute SQL")
        ds.connection.use { conn ->
            conn.createStatement().use { it.execute(sql) }
        }
    }

    /**
     * Runs Flyway migrations programmatically. Used by RLS tests that need to create tables
     * first and then apply RLS policies via the repeatable migration.
     */
    protected fun migrate() {
        val ds = adminDataSource ?: error("adminDataSource is required to run migrations")
        val flywayConfig =
            Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration/postgresql")
                .baselineOnMigrate(true)
                .placeholderReplacement(true)
                .placeholders(
                    mapOf("migration_timestamp" to System.currentTimeMillis().toString())
                )
        flywayCustomizer?.customize(flywayConfig)
        flywayConfig.load().migrate()
    }
}
