package com.github.atrifyllis.multitenancy

import com.github.atrifyllis.multitenancy.application.service.TenantContext
import java.sql.DriverManager
import java.time.Duration
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [TestSecurityConfig::class],
)
@Testcontainers
@AutoConfigureMockMvc
open class BasePostgresTest {

    @Autowired(required = false)
    var flywayCustomizer: FlywayConfigurationCustomizer? = null

    companion object {
        val postgresContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16-alpine")
                .withTmpFs(mapOf("/var/lib/postgresql/data" to "rw"))
                .withUsername("core")
                .withPassword("core")
                .withCommand("postgres", "-c", "fsync=off", "-c", "log_statement=all")
                .withStartupTimeout(Duration.ofSeconds(90))
                .withReuse(true)

        init {
            postgresContainer.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun initialize(registry: DynamicPropertyRegistry) {
            registry.add("multitenancy.admin.datasource.url", postgresContainer::getJdbcUrl)
            registry.add("multitenancy.tenant.datasource.url", postgresContainer::getJdbcUrl)

            registry.add("multitenancy.admin.datasource.username", postgresContainer::getUsername)
            registry.add("multitenancy.admin.datasource.password", postgresContainer::getPassword)
            registry.add("multitenancy.tenant.datasource.username", postgresContainer::getUsername)
            registry.add("multitenancy.tenant.datasource.password", postgresContainer::getPassword)

            registry.add("multitenancy.tenant-jpa-base-package") {
                "com.github.atrifyllis.multitenancy"
            }
            registry.add("multitenancy.admin-jpa-packages-to-scan") {
                "com.github.atrifyllis.multitenancy.admin"
            }
        }
    }

    @AfterEach
    fun clearTenantContext() {
        TenantContext.clear()
    }

    protected fun exec(sql: String) {
        DriverManager.getConnection(
            postgresContainer.jdbcUrl,
            postgresContainer.username,
            postgresContainer.password,
        ).use { conn ->
            conn.createStatement().use { it.execute(sql) }
        }
    }

    protected fun migrate() {
        com.zaxxer.hikari.HikariDataSource().apply {
            jdbcUrl = postgresContainer.jdbcUrl
            username = postgresContainer.username
            password = postgresContainer.password
        }.use { ds ->
            val fluent =
                Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration/postgresql")
                    .baselineOnMigrate(true)
                    .placeholderReplacement(true)
                    .placeholders(
                        mapOf("migration_timestamp" to System.currentTimeMillis().toString())
                    )
            flywayCustomizer?.customize(fluent)
            fluent.load().migrate()
        }
    }
}
