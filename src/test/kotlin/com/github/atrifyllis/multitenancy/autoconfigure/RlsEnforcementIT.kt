package com.github.atrifyllis.multitenancy.autoconfigure

import com.github.atrifyllis.multitenancy.BasePostgresIT
import com.github.atrifyllis.multitenancy.adapters.secondary.persistence.TenantAwareDataSource
import com.github.atrifyllis.multitenancy.application.service.TenantContext
import com.zaxxer.hikari.HikariDataSource
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * End-to-end test that verifies PostgreSQL Row-Level Security actually enforces tenant isolation.
 *
 * Unlike [RlsRepeatableMigrationIT] which only checks that policies are created, this test proves
 * that data inserted by tenant A is invisible to tenant B.
 *
 * Important: RLS policies do not apply to table owners or superusers. This test creates a
 * non-superuser role (`app_user`) for tenant-scoped queries.
 */
class RlsEnforcementIT : BasePostgresIT() {

    companion object {
        private const val APP_USER = "app_user"
        private const val APP_PASSWORD = "app_password"
        private const val TABLE_NAME = "rls_enforcement_test"
    }

    private val tenantA: UUID = UUID.randomUUID()
    private val tenantB: UUID = UUID.randomUUID()

    private lateinit var tenantDataSource: TenantAwareDataSource

    private val commonProps =
        arrayOf(
            "multitenancy.enabled=true",
            "multitenancy.rls.enabled=true",
            "multitenancy.rls.schema=public",
            "multitenancy.rls.tenant-column=tenant_id",
            "multitenancy.rls.policy-name=tenant_isolation_policy",
            "multitenancy.rls.exclude-tables=flyway_schema_history,excluded",
        )

    @BeforeAll
    fun setupRls() {
        exec(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                id UUID PRIMARY KEY,
                name VARCHAR(255),
                tenant_id UUID
            )
            """
        )

        // Create a non-superuser role (RLS doesn't apply to table owners/superusers)
        exec(
            """
            DO $$ BEGIN
                IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$APP_USER') THEN
                    CREATE ROLE $APP_USER LOGIN PASSWORD '$APP_PASSWORD';
                END IF;
            END $$
            """
        )
        exec("GRANT USAGE ON SCHEMA public TO $APP_USER")
        exec("GRANT SELECT, INSERT, UPDATE, DELETE ON $TABLE_NAME TO $APP_USER")

        // Run Flyway to apply RLS policies
        withFlyway(*commonProps) { flyway -> flyway.migrate() }

        // Tenant-aware datasource using non-superuser for actual queries
        val appUserDataSource =
            HikariDataSource().apply {
                jdbcUrl = postgresContainer.jdbcUrl
                username = APP_USER
                password = APP_PASSWORD
            }
        tenantDataSource = TenantAwareDataSource(appUserDataSource)
    }

    @AfterEach
    fun cleanupData() {
        TenantContext.clear()
        exec("DELETE FROM $TABLE_NAME")
    }

    @Test
    fun `tenant A cannot see tenant B data`() {
        insertRow(tenantA, "tenant-a-row")

        TenantContext.setTenantId(tenantB)
        tenantDataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COUNT(*) FROM $TABLE_NAME").use { rs ->
                    rs.next()
                    assertThat(rs.getInt(1)).isEqualTo(0)
                }
            }
        }
    }

    @Test
    fun `tenant sees only own data`() {
        insertRow(tenantA, "a-row-1")
        insertRow(tenantA, "a-row-2")
        insertRow(tenantB, "b-row-1")

        TenantContext.setTenantId(tenantA)
        tenantDataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT name FROM $TABLE_NAME ORDER BY name").use { rs ->
                    val names = mutableListOf<String>()
                    while (rs.next()) names.add(rs.getString(1))
                    assertThat(names).containsExactly("a-row-1", "a-row-2")
                }
            }
        }
    }

    @Test
    fun `tenant cannot update another tenant's data`() {
        insertRow(tenantA, "original")

        TenantContext.setTenantId(tenantB)
        tenantDataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                val updated = stmt.executeUpdate(
                    "UPDATE $TABLE_NAME SET name = 'hacked' WHERE name = 'original'"
                )
                assertThat(updated).isEqualTo(0)
            }
        }

        TenantContext.setTenantId(tenantA)
        tenantDataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT name FROM $TABLE_NAME").use { rs ->
                    rs.next()
                    assertThat(rs.getString(1)).isEqualTo("original")
                }
            }
        }
    }

    @Test
    fun `tenant cannot delete another tenant's data`() {
        insertRow(tenantA, "protected")

        TenantContext.setTenantId(tenantB)
        tenantDataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                val deleted = stmt.executeUpdate("DELETE FROM $TABLE_NAME WHERE name = 'protected'")
                assertThat(deleted).isEqualTo(0)
            }
        }

        TenantContext.setTenantId(tenantA)
        tenantDataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COUNT(*) FROM $TABLE_NAME").use { rs ->
                    rs.next()
                    assertThat(rs.getInt(1)).isEqualTo(1)
                }
            }
        }
    }

    private fun insertRow(tenantId: UUID, name: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO $TABLE_NAME (id, name, tenant_id) VALUES (?, ?, ?)"
            ).use { ps ->
                ps.setObject(1, UUID.randomUUID())
                ps.setString(2, name)
                ps.setObject(3, tenantId)
                ps.executeUpdate()
            }
        }
    }
}
