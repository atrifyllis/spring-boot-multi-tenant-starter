package com.github.atrifyllis.multitenancy

import com.github.atrifyllis.multitenancy.autoconfigure.RlsAutoConfiguration
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * Base class for standalone integration tests that need a PostgreSQL container
 * but not a full Spring Boot context.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class BasePostgresIT : BasePostgresContainer() {

    protected lateinit var dataSource: HikariDataSource

    @BeforeAll
    fun initDataSource() {
        dataSource =
            HikariDataSource().apply {
                jdbcUrl = postgresContainer.jdbcUrl
                username = postgresContainer.username
                password = postgresContainer.password
            }
    }

    @AfterAll
    fun closeDataSource() {
        dataSource.close()
    }

    protected fun exec(sql: String) {
        dataSource.connection.use { conn ->
            conn.createStatement().use { it.execute(sql) }
        }
    }

    protected fun withFlyway(vararg props: String, block: (Flyway) -> Unit) {
        val ctxRunner =
            ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RlsAutoConfiguration::class.java))
                .withPropertyValues(*props)
        ctxRunner.run { ctx ->
            val customizer = ctx.getBean(FlywayConfigurationCustomizer::class.java)
            val fluent =
                Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration/postgresql")
                    .baselineOnMigrate(true)
                    .placeholderReplacement(true)
                    .placeholders(
                        mapOf("migration_timestamp" to System.currentTimeMillis().toString())
                    )
            customizer.customize(fluent)
            val flyway = fluent.load()
            block(flyway)
        }
    }
}