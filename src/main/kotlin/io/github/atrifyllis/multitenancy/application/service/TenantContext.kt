package io.github.atrifyllis.multitenancy.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

object TenantContext {
    private val logger = KotlinLogging.logger {}
    private val currentTenant: InheritableThreadLocal<UUID> = InheritableThreadLocal()

    fun setTenantId(tenantId: UUID) {
        logger.debug { "Setting tenantId to $tenantId" }
        currentTenant.set(tenantId)
    }

    fun getTenantId(): UUID? = currentTenant.get()

    fun clear() {
        currentTenant.remove()
    }
}
