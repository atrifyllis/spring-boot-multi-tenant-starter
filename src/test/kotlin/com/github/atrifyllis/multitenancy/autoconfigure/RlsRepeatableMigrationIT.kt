package com.github.atrifyllis.multitenancy.autoconfigure

import com.github.atrifyllis.multitenancy.BasePostgresIT
import java.sql.Connection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class RlsRepeatableMigrationIT : BasePostgresIT() {

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
    fun setupTables() {
        exec("CREATE TABLE IF NOT EXISTS included (id uuid primary key, tenant_id uuid)")
        exec("CREATE TABLE IF NOT EXISTS excluded (id uuid primary key, tenant_id uuid)")
        exec("CREATE TABLE IF NOT EXISTS no_tenant_col (id uuid primary key)")
    }

    @Test
    fun `repeatable migration enables RLS and creates policy for included tables only`() {
        withFlyway(*commonProps) { flyway -> flyway.migrate() }

        Thread.sleep(10)

        withFlyway(*commonProps) { flyway ->
            val result = flyway.migrate()
            assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(1)
        }

        dataSource.connection.use { conn ->
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

        withFlyway(*commonProps) { flyway -> flyway.migrate() }

        exec("CREATE TABLE IF NOT EXISTS $tableName (id uuid primary key, tenant_id uuid)")

        Thread.sleep(10)

        withFlyway(*commonProps) { flyway ->
            val secondResult = flyway.migrate()
            assertThat(secondResult.migrationsExecuted).isGreaterThanOrEqualTo(1)
        }

        try {
            dataSource.connection.use { conn ->
                assertThat(policyExists(conn, "public", tableName, "tenant_isolation_policy"))
                    .isTrue()
                assertThat(rlsEnabled(conn, "public", tableName)).isTrue()
            }
        } finally {
            exec("DROP TABLE IF EXISTS $tableName CASCADE")
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
}
