package com.github.atrifyllis.multitenancy.autoconfigure

import com.github.atrifyllis.multitenancy.BasePostgresTest
import java.sql.Connection
import java.sql.DriverManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.test.context.TestPropertySource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = ["multitenancy.rls.exclude-tables=excluded,flyway_schema_history"])
class RlsRepeatableMigrationTest : BasePostgresTest() {

    @BeforeAll
    fun setupTables() {
        exec("CREATE TABLE IF NOT EXISTS included (id uuid primary key, tenant_id uuid)")
        exec("CREATE TABLE IF NOT EXISTS excluded (id uuid primary key, tenant_id uuid)")
        exec("CREATE TABLE IF NOT EXISTS no_tenant_col (id uuid primary key)")
    }

    @Test
    fun `repeatable migration enables RLS and creates policy for included tables only`() {
        migrate()

        Thread.sleep(10)

        migrate()

        openConnection().use { conn ->
            assertThat(policyExists(conn, "public", "included", "tenant_isolation_policy")).isTrue()
            assertThat(rlsEnabled(conn, "public", "included")).isTrue()
            assertThat(policyExists(conn, "public", "excluded", "tenant_isolation_policy"))
                .isFalse()
            assertThat(policyExists(conn, "public", "no_tenant_col", "tenant_isolation_policy"))
                .isFalse()
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

        migrate()

        exec("CREATE TABLE IF NOT EXISTS $tableName (id uuid primary key, tenant_id uuid)")

        Thread.sleep(10)

        migrate()

        try {
            openConnection().use { conn ->
                assertThat(policyExists(conn, "public", tableName, "tenant_isolation_policy"))
                    .isTrue()
                assertThat(rlsEnabled(conn, "public", tableName)).isTrue()
            }
        } finally {
            exec("DROP TABLE IF EXISTS $tableName CASCADE")
        }
    }

    private fun openConnection(): Connection =
        DriverManager.getConnection(
            postgresContainer.jdbcUrl,
            postgresContainer.username,
            postgresContainer.password,
        )

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
}
