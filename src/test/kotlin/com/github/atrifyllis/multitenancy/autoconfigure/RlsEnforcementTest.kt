package com.github.atrifyllis.multitenancy.autoconfigure

import com.github.atrifyllis.multitenancy.BasePostgresTest
import com.github.atrifyllis.multitenancy.application.service.TenantContext
import java.util.*
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.test.context.TestPropertySource

/**
 * End-to-end test that verifies PostgreSQL Row-Level Security actually enforces tenant isolation.
 *
 * Unlike [RlsRepeatableMigrationTest] which only checks that policies are created, this test proves
 * that data inserted by tenant A is invisible to tenant B.
 *
 * The tenant datasource connects as `app_user` (a non-owner role created by the container init
 * script), so RLS policies are enforced. The admin datasource connects as `core` (the table owner),
 * which bypasses RLS — used here to insert test data across tenants.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = ["multitenancy.rls.exclude-tables=flyway_schema_history,excluded"])
class RlsEnforcementTest : BasePostgresTest() {

    companion object {
        private const val TABLE_NAME = "rls_enforcement_test"
    }

    @Autowired
    @Qualifier("tenantDataSource")
    lateinit var tenantDataSource: DataSource

    private val tenantA: UUID = UUID.randomUUID()
    private val tenantB: UUID = UUID.randomUUID()

    @BeforeAll
    fun setupRls() {
        adminExec(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                id UUID PRIMARY KEY,
                name VARCHAR(255),
                tenant_id UUID
            )
            """
        )

        // Run Flyway to apply RLS policies
        migrate()
    }

    @AfterEach
    fun cleanupData() {
        TenantContext.clear()
        adminExec("DELETE FROM $TABLE_NAME")
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
        adminDataSource!!.connection.use { conn ->
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
