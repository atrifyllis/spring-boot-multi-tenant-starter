package com.github.atrifyllis.multitenancy.adapters.secondary.persistence

import com.github.atrifyllis.multitenancy.application.service.TenantContext
import java.time.Duration
import java.util.*
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantAwareDataSourceIT {

    companion object {
        private val postgresContainer: PostgreSQLContainer<*> =
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

    @BeforeAll
    fun setup() {
        java.sql.DriverManager.getConnection(
                postgresContainer.jdbcUrl,
                postgresContainer.username,
                postgresContainer.password,
            )
            .use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS test_entity (
                            id UUID PRIMARY KEY,
                            name VARCHAR(255),
                            tenant_id UUID
                        )
                        """
                    )
                }
            }
    }

    @AfterEach
    fun cleanup() {
        TenantContext.clear()
    }

    @Test
    fun `tenant_id session variable is set within Spring-managed transaction`() {
        val tenantId = UUID.randomUUID()
        TenantContext.setTenantId(tenantId)

        val tenantDs = TenantAwareDataSource(createPlainDataSource())
        val txManager =
            org.springframework.jdbc.datasource.DataSourceTransactionManager(tenantDs)
        val txTemplate = TransactionTemplate(txManager)

        txTemplate.execute { _ ->
            // Get connection through DataSourceUtils (how Spring gets connections in @Transactional)
            val conn = DataSourceUtils.getConnection(tenantDs)
            try {
                // Verify tenant_id session variable is set
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT current_setting('app.tenant_id')").use { rs ->
                        rs.next()
                        assertThat(rs.getString(1)).isEqualTo(tenantId.toString())
                    }
                }

                // Insert a row to prove the connection works for DML within a transaction
                conn.prepareStatement(
                        "INSERT INTO test_entity (id, name, tenant_id) VALUES (?, ?, ?)"
                    )
                    .use { ps ->
                        ps.setObject(1, UUID.randomUUID())
                        ps.setString(2, "transactional-test")
                        ps.setObject(3, tenantId)
                        assertThat(ps.executeUpdate()).isEqualTo(1)
                    }
            } finally {
                DataSourceUtils.releaseConnection(conn, tenantDs)
            }
        }
    }

    @Test
    fun `multiple getConnection calls within same transaction return same underlying connection`() {
        val tenantId = UUID.randomUUID()
        TenantContext.setTenantId(tenantId)

        val tenantDs = TenantAwareDataSource(createPlainDataSource())
        val txManager =
            org.springframework.jdbc.datasource.DataSourceTransactionManager(tenantDs)
        val txTemplate = TransactionTemplate(txManager)

        txTemplate.execute { _ ->
            val conn1 = DataSourceUtils.getConnection(tenantDs)
            val conn2 = DataSourceUtils.getConnection(tenantDs)

            // Spring should return the same connection within the same transaction
            assertThat(conn1).isSameAs(conn2)

            DataSourceUtils.releaseConnection(conn2, tenantDs)
            DataSourceUtils.releaseConnection(conn1, tenantDs)
        }
    }

    private fun createPlainDataSource(): DataSource {
        return com.zaxxer.hikari.HikariDataSource().apply {
            jdbcUrl = postgresContainer.jdbcUrl
            username = postgresContainer.username
            password = postgresContainer.password
        }
    }
}
