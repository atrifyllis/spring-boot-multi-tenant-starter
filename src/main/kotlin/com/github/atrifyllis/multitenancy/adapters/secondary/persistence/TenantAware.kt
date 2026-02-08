package com.github.atrifyllis.multitenancy.adapters.secondary.persistence

import java.util.*

fun interface TenantAware {
    fun setTenantId(tenantId: UUID?)
}
