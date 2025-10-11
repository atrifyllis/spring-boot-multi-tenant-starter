package io.github.atrifyllis.multitenancy.adapters.secondary.persistence

import io.github.atrifyllis.multitenancy.application.service.TenantContext
import java.util.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

private class DummyTenantAware : TenantAware {
    var received: UUID? = null

    override fun setTenantId(tenantId: UUID?) {
        received = tenantId
    }
}

class TenantListenerTest {

    private val listener = TenantListener()

    @AfterEach
    fun cleanup() {
        TenantContext.clear()
    }

    @Test
    fun `sets tenant id from TenantContext`() {
        val tenantId = UUID.randomUUID()
        TenantContext.setTenantId(tenantId)
        val entity = DummyTenantAware()

        listener.setTenant(entity)

        assertEquals(tenantId, entity.received)
    }

    @Test
    fun `sets null when no tenant in context`() {
        val entity = DummyTenantAware()

        listener.setTenant(entity)

        assertNull(entity.received)
    }
}
