package com.github.atrifyllis.multitenancy.adapters.secondary.persistence

import com.github.atrifyllis.multitenancy.application.service.TenantContext
import java.sql.SQLException
import java.sql.Statement
import java.util.*
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

/** Unit test for connection-leak bug in TenantAwareDataSource. No Docker/Testcontainers required. */
class TenantAwareDataSourceConnectionLeakTest {

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
    }

    @Test
    fun `close() is called on underlying connection even when RESET throws`() {
        // Arrange: first createStatement() succeeds (SET), second throws (RESET)
        val setStatement = mock(Statement::class.java)
        val resetStatement = mock(Statement::class.java)
        Mockito.`when`(resetStatement.execute(anyString()))
            .thenThrow(SQLException("Simulated RESET failure"))

        val mockConn = mock(java.sql.Connection::class.java)
        Mockito.`when`(mockConn.createStatement())
            .thenReturn(setStatement)   // first call → SET succeeds
            .thenReturn(resetStatement) // second call → RESET throws

        val mockDs = mock(DataSource::class.java)
        Mockito.`when`(mockDs.connection).thenReturn(mockConn)

        TenantContext.setTenantId(UUID.randomUUID())
        val ds = TenantAwareDataSource(mockDs)
        val proxyConn = ds.connection // triggers SET — succeeds

        // Act + Assert: close() must not propagate the RESET exception,
        // AND the real connection.close() must still be called.
        assertThatCode { proxyConn.close() }.doesNotThrowAnyException()
        verify(mockConn).close()
    }
}
