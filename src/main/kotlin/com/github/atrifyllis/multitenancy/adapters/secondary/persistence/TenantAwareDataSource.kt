package com.github.atrifyllis.multitenancy.adapters.secondary.persistence

import com.github.atrifyllis.multitenancy.application.service.TenantContext
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException
import java.util.*
import javax.sql.DataSource
import org.springframework.jdbc.datasource.DelegatingDataSource

/** Tenant-Aware Datasource that decorates Connections with current tenant information. */
class TenantAwareDataSource(private val targetDataSource: DataSource) :
    DelegatingDataSource(targetDataSource) {

    override fun getConnection(): Connection {
        val connection = targetDataSource.connection
        setTenantId(connection)
        return getTenantAwareConnectionProxy(connection)
    }

    override fun getConnection(username: String, password: String): Connection {
        val connection = targetDataSource.getConnection(username, password)
        setTenantId(connection)
        return getTenantAwareConnectionProxy(connection)
    }

    @Throws(SQLException::class)
    private fun setTenantId(connection: Connection) {
        connection.createStatement().use { sql ->
            val tenantId: UUID? = TenantContext.getTenantId()
            // NOTE: this could be considered sql injection vulnerable, but since tenantId is UUID,
            // it's safe
            sql.execute("SET app.tenant_id TO '$tenantId'")
        }
    }

    @Throws(SQLException::class)
    private fun clearTenantId(connection: Connection) {
        connection.createStatement().use { sql -> sql.execute("RESET app.tenant_id") }
    }

    private fun getTenantAwareConnectionProxy(connection: Connection): Connection {
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(Connection::class.java),
            TenantAwareInvocationHandler(connection)
        ) as Connection
    }

    // Connection Proxy invocation handler that intercepts close() to reset the tenant_id
    private inner class TenantAwareInvocationHandler(private val target: Connection) :
        InvocationHandler {
        @Throws(Throwable::class)
        override fun invoke(proxy: Any, method: Method, args: Array<Any>?): Any? {
            if (method.name == "close") {
                clearTenantId(target)
            }
            return method.invoke(target, *(args ?: emptyArray()))
        }
    }
}
