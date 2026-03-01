package com.github.atrifyllis.multitenancy

import com.github.atrifyllis.multitenancy.application.service.TenantContext
import java.time.Duration
import org.junit.jupiter.api.AfterEach
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base class providing a shared PostgreSQL testcontainer.
 */
@Testcontainers
open class BasePostgresContainer {

    companion object {
        val postgresContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16-alpine")
                .withTmpFs(mapOf("/var/lib/postgresql/data" to "rw"))
                .withUsername("core")
                .withPassword("core")
                .withCommand("postgres", "-c", "fsync=off", "-c", "log_statement=all")
                .withStartupTimeout(Duration.ofSeconds(90))
                .withReuse(true)

        init {
            postgresContainer.start()
        }
    }

    @AfterEach
    fun clearTenantContext() {
        TenantContext.clear()
    }
}