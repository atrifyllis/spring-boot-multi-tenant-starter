package com.github.atrifyllis.multitenancy.autoconfigure

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
@EnableConfigurationProperties(MultitenancyRlsPropertiesHolder::class)
@ConditionalOnProperty(
    prefix = "multitenancy",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class RlsAutoConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "multitenancy.rls",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun rlsFlywayCustomizer(
        propsHolder: MultitenancyRlsPropertiesHolder
    ): FlywayConfigurationCustomizer = FlywayConfigurationCustomizer { cfg ->
        val props = propsHolder.asProps()
        val placeholders = java.util.HashMap<String, String>()
        val existing = cfg.placeholders
        if (existing != null) placeholders.putAll(existing)
        placeholders["rls_enabled"] = props.enabled.toString()
        placeholders["rls_schema"] = props.schema
        placeholders["rls_tenant_column"] = props.tenantColumn
        placeholders["rls_policy_name"] = props.policyName
        placeholders["rls_exclude_tables"] = props.excludeTables.joinToString(",")
        // Set migration_timestamp to force repeatable migration to re-run when needed
        placeholders["migration_timestamp"] = System.currentTimeMillis().toString()
        cfg.placeholders(placeholders)
    }
}
