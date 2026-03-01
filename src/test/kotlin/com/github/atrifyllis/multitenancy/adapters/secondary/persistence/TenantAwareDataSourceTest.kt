package com.github.atrifyllis.multitenancy.adapters.secondary.persistence

import com.github.atrifyllis.multitenancy.BasePostgresTest
import com.github.atrifyllis.multitenancy.application.service.TenantContext
import java.util.*
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantAwareDataSourceTest : BasePostgresTest() {

    @Autowired
    @Qualifier("tenantDataSource")
    lateinit var tenantDataSource: DataSource

    @Autowired
    lateinit var transactionManager: PlatformTransactionManager

    @BeforeAll
    fun setupTable() {
        adminExec(
            """
            CREATE TABLE IF NOT EXISTS test_entity (
                id UUID PRIMARY KEY,
                name VARCHAR(255),
                tenant_id UUID
            )
            """
        )
    }

    @Test
    fun `tenant_id session variable is set within Spring-managed transaction`() {
        val tenantId = UUID.randomUUID()
        TenantContext.setTenantId(tenantId)

        val txTemplate = TransactionTemplate(transactionManager)

        txTemplate.execute { _ ->
            val conn = DataSourceUtils.getConnection(tenantDataSource)
            try {
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT current_setting('app.tenant_id')").use { rs ->
                        rs.next()
                        assertThat(rs.getString(1)).isEqualTo(tenantId.toString())
                    }
                }

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
                DataSourceUtils.releaseConnection(conn, tenantDataSource)
            }
        }
    }

    @Test
    fun `multiple getConnection calls within same transaction return same underlying connection`() {
        val tenantId = UUID.randomUUID()
        TenantContext.setTenantId(tenantId)

        val txTemplate = TransactionTemplate(transactionManager)

        txTemplate.execute { _ ->
            val conn1 = DataSourceUtils.getConnection(tenantDataSource)
            val conn2 = DataSourceUtils.getConnection(tenantDataSource)

            assertThat(conn1).isSameAs(conn2)

            DataSourceUtils.releaseConnection(conn2, tenantDataSource)
            DataSourceUtils.releaseConnection(conn1, tenantDataSource)
        }
    }
}
