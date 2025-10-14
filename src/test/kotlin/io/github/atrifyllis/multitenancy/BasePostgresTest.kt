package io.github.atrifyllis.multitenancy

import io.github.atrifyllis.multitenancy.application.service.TenantContext
import org.junit.jupiter.api.AfterEach
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [TestSecurityConfig::class],
)
@Testcontainers
@AutoConfigureMockMvc // autowire MockMvc
class BasePostgresTest {


    companion object {

        //        @Container
        val postgresContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:alpine3.19")
                .withTmpFs(mapOf("/var/lib/postgresql/data" to "rw"))
                .withUsername("core")
                .withPassword("core")
                .withCommand("postgres", "-c", "fsync=off", "-c", "log_statement=all")
                .withReuse(true)

        init {
            postgresContainer.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun initialize(registry: DynamicPropertyRegistry) {
            registry.add("multitenancy.admin.datasource.url", postgresContainer::getJdbcUrl)
            registry.add("multitenancy.tenant.datasource.url", postgresContainer::getJdbcUrl)

            registry.add("multitenancy.admin.datasource.username", postgresContainer::getUsername)
            registry.add("multitenancy.admin.datasource.password", postgresContainer::getPassword)
            registry.add("multitenancy.tenant.datasource.username", postgresContainer::getUsername)
            registry.add("multitenancy.tenant.datasource.password", postgresContainer::getPassword)

            registry.add("multitenancy.tenant-jpa-base-package") { "io.github.atrifyllis.multitenancy" }
            registry.add("multitenancy.admin-jpa-packages-to-scan") { "io.github.atrifyllis.multitenancy.admin" }
        }
    }

    @AfterEach
    fun tearDown() {

        TenantContext.clear()
    }

}
