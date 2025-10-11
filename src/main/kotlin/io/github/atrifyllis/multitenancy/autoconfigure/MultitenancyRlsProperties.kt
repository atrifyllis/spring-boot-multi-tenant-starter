package io.github.atrifyllis.multitenancy.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

data class MultitenancyRlsProperties(
    val enabled: Boolean = true,
    val schema: String = "public",
    val tenantColumn: String = "tenant_id",
    val policyName: String = "tenant_isolation_policy",
    val excludeTables: List<String> = emptyList(),
)

@ConfigurationProperties("multitenancy.rls")
class MultitenancyRlsPropertiesHolder(
    var enabled: Boolean = true,
    var schema: String = "public",
    var tenantColumn: String = "tenant_id",
    var policyName: String = "tenant_isolation_policy",
    var excludeTables: List<String> = emptyList(),
) {
    fun asProps(): MultitenancyRlsProperties =
        MultitenancyRlsProperties(enabled, schema, tenantColumn, policyName, excludeTables)
}
