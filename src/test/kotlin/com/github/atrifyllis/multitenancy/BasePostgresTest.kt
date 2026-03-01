package com.github.atrifyllis.multitenancy

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [TestSecurityConfig::class],
)
@AutoConfigureMockMvc
class BasePostgresTest : BasePostgresContainer() {

    companion object {

        @JvmStatic
        @DynamicPropertySource
        fun initialize(registry: DynamicPropertyRegistry) {
            registry.add("multitenancy.admin.datasource.url", postgresContainer::getJdbcUrl)
            registry.add("multitenancy.tenant.datasource.url", postgresContainer::getJdbcUrl)

            registry.add("multitenancy.admin.datasource.username", postgresContainer::getUsername)
            registry.add("multitenancy.admin.datasource.password", postgresContainer::getPassword)
            registry.add("multitenancy.tenant.datasource.username", postgresContainer::getUsername)
            registry.add("multitenancy.tenant.datasource.password", postgresContainer::getPassword)

            registry.add("multitenancy.tenant-jpa-base-package") {
                "com.github.atrifyllis.multitenancy"
            }
            registry.add("multitenancy.admin-jpa-packages-to-scan") {
                "com.github.atrifyllis.multitenancy.admin"
            }
        }
    }
}