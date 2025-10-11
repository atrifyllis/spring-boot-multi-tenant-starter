package io.github.atrifyllis.multitenancy.adapters.secondary.persistence

import io.github.atrifyllis.multitenancy.application.service.TenantContext
import jakarta.persistence.PrePersist
import jakarta.persistence.PreRemove
import jakarta.persistence.PreUpdate

class TenantListener {
    @PreUpdate
    @PreRemove
    @PrePersist
    fun setTenant(entity: TenantAware) {
        val tenantId = TenantContext.getTenantId()
        entity.setTenantId(tenantId)
    }
}
