package io.github.atrifyllis.multitenancy.adapters.secondary.persistence

import io.github.atrifyllis.multitenancy.application.service.TenantContext
import java.sql.Connection
import java.sql.Statement
import java.util.*
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class TenantAwareDataSourceTest {

    @AfterEach
    fun cleanup() {
        TenantContext.clear()
    }

    @Test
    fun `sets and clears tenant id via SQL on connection lifecycle`() {
        // Given
        val tenantId = UUID.randomUUID()
        TenantContext.setTenantId(tenantId)

        val mockStatement = mock(Statement::class.java)
        val mockConnection = mock(Connection::class.java)
        `when`(mockConnection.createStatement()).thenReturn(mockStatement)

        val mockDataSource = mock(DataSource::class.java)
        `when`(mockDataSource.connection).thenReturn(mockConnection)

        // When
        val tenantAwareDataSource = TenantAwareDataSource(mockDataSource)
        val connection = tenantAwareDataSource.connection
        connection.close()

        // Then
        verify(mockStatement).execute("SET app.tenant_id TO '$tenantId'")
        verify(mockStatement).execute("RESET app.tenant_id")
    }

    @Test
    fun `all proxied connections share the same proxy class`() {
        val mockStatement = mock(Statement::class.java)
        val mockConnection = mock(Connection::class.java)
        `when`(mockConnection.createStatement()).thenReturn(mockStatement)

        val mockDataSource = mock(DataSource::class.java)
        `when`(mockDataSource.connection).thenReturn(mockConnection)

        val tenantAwareDataSource = TenantAwareDataSource(mockDataSource)

        val classes = (1..100).map { tenantAwareDataSource.connection.javaClass }.toSet()

        assertThat(classes).hasSize(1)
    }
}
