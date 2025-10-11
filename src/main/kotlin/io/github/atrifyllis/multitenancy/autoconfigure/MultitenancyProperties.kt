package io.github.atrifyllis.multitenancy.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("multitenancy")
data class MultitenancyProperties(
    val enabled: Boolean = true,
    /**
     * The base package to scan for JPA entities and repositories.
     */
    val tenantJpaBasePackage: String
)
