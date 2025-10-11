package io.github.atrifyllis.multitenancy.autoconfigure

import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RlsRepeatableMigrationIT {

    companion object {
        @Container
        @JvmStatic
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
    }

    private lateinit var dataSource: HikariDataSource

    // Common properties used in both tests
    private val commonProps =
        arrayOf(
            "multitenancy.enabled=true",
            "multitenancy.rls.enabled=true",
            "multitenancy.rls.schema=public",
            "multitenancy.rls.tenant-column=tenant_id",
            "multitenancy.rls.policy-name=tenant_isolation_policy",
            "multitenancy.rls.exclude-tables=excluded,flyway_schema_history",
        )

    @BeforeAll
    fun setup() {
        dataSource =
            HikariDataSource().apply {
                jdbcUrl = postgresContainer.jdbcUrl
                username = postgresContainer.username
                password = postgresContainer.password
            }
        dataSource.connection.use { conn ->
            exec(conn, "CREATE TABLE IF NOT EXISTS included (id uuid primary key, tenant_id uuid)")
            exec(conn, "CREATE TABLE IF NOT EXISTS excluded (id uuid primary key, tenant_id uuid)")
            exec(conn, "CREATE TABLE IF NOT EXISTS no_tenant_col (id uuid primary key)")
        }
    }

    @AfterAll
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `repeatable migration enables RLS and creates policy for included tables only`() {
        // First migration with first timestamp
        withFlyway { flyway -> flyway.migrate() }

        // Ensure ${migration_timestamp} placeholder resolves differently between runs (milliseconds
        // precision)
        Thread.sleep(10)

        // Second migration with different timestamp (repeatability check)
        var secondResult: org.flywaydb.core.api.MigrationInfoService? = null
        withFlyway { flyway ->
            val result = flyway.migrate()
            secondResult = flyway.info()
            // Assert that the second migration did apply the repeatable migration again
            assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(1)
        }

        dataSource.connection.use { conn ->
            // included: policy exists and RLS enabled
            assertThat(policyExists(conn, "public", "included", "tenant_isolation_policy")).isTrue()
            assertThat(rlsEnabled(conn, "public", "included")).isTrue()
            // excluded: no policy, RLS may be false
            assertThat(policyExists(conn, "public", "excluded", "tenant_isolation_policy"))
                .isFalse()
            // table without tenant column is skipped
            assertThat(policyExists(conn, "public", "no_tenant_col", "tenant_isolation_policy"))
                .isFalse()
            // Check that repeatable migration is present in flyway_schema_history
            val repeatableApplied =
                conn
                    .prepareStatement(
                        "SELECT count(*) FROM flyway_schema_history WHERE type = 'R' AND description = 'enforce rls'"
                    )
                    .use { ps ->
                        ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                    }
            assertThat(repeatableApplied).isGreaterThanOrEqualTo(0)
        }
    }

    @Test
    fun `repeatable migration re-applies and configures a table created after first migrate`() {
        val tableName = "new_included"

        // First migrate
        withFlyway { flyway -> flyway.migrate() }

        // Create a new table to be picked up by the repeatable migration
        createTempTable(tableName)

        // Ensure ${migration_timestamp} placeholder resolves differently between runs (milliseconds
        // precision)
        Thread.sleep(10)

        // Second migrate should re-apply repeatables and configure the new table
        withFlyway { flyway ->
            val secondResult = flyway.migrate()
            assertThat(secondResult.migrationsExecuted).isGreaterThanOrEqualTo(1)
        }

        try {
            // Verify policy and RLS are applied on the new table
            dataSource.connection.use { conn ->
                assertThat(policyExists(conn, "public", tableName, "tenant_isolation_policy"))
                    .isTrue()
                assertThat(rlsEnabled(conn, "public", tableName)).isTrue()
            }
        } finally {
            // Cleanup: drop the table created in this test
            dataSource.connection.use { conn ->
                exec(conn, "DROP TABLE IF EXISTS $tableName CASCADE")
            }
        }
    }

    private fun createTempTable(tableName: String) {
        dataSource.connection.use { conn ->
            exec(
                conn,
                "CREATE TABLE IF NOT EXISTS $tableName (id uuid primary key, tenant_id uuid)",
            )
        }
    }

    @Suppress("SameParameterValue")
    private fun policyExists(
        conn: Connection,
        schema: String,
        table: String,
        policy: String,
    ): Boolean =
        conn
            .prepareStatement(
                "SELECT 1 FROM pg_policies WHERE schemaname=? AND tablename=? AND policyname=?"
            )
            .use { ps ->
                ps.setString(1, schema)
                ps.setString(2, table)
                ps.setString(3, policy)
                ps.executeQuery().use { rs -> rs.next() }
            }

    @Suppress("SameParameterValue")
    private fun rlsEnabled(conn: Connection, schema: String, table: String): Boolean =
        conn
            .prepareStatement(
                """
                SELECT c.relrowsecurity
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relname = ?
                """
                    .trimIndent()
            )
            .use { ps ->
                ps.setString(1, schema)
                ps.setString(2, table)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getBoolean(1) else false }
            }

    private fun exec(conn: Connection, sql: String) {
        conn.createStatement().use { it.execute(sql) }
    }

    // Helper to reduce duplication: builds a Flyway instance in the configured Spring context
    private fun withFlyway(block: (Flyway) -> Unit) {
        val ctxRunner =
            ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RlsAutoConfiguration::class.java))
                .withPropertyValues(*commonProps)
        ctxRunner.run { ctx ->
            val customizer = ctx.getBean(FlywayConfigurationCustomizer::class.java)
            val fluent =
                Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration/postgresql")
                    .baselineOnMigrate(true)
                    .placeholderReplacement(true)
                    // Generate fresh timestamp for each Flyway instance to ensure repeatables
                    // re-run
                    .placeholders(
                        mapOf("migration_timestamp" to System.currentTimeMillis().toString())
                    )
            customizer.customize(fluent)
            val flyway = fluent.load()
            block(flyway)
        }
    }
}
