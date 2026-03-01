package com.github.atrifyllis.multitenancy

import com.github.atrifyllis.multitenancy.application.service.TenantContext
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Minimal Spring Boot application used by integration tests.
 *
 * [JpaRepositoriesAutoConfiguration] is excluded because it expects a bean named
 * `entityManagerFactory`, but the starter creates `adminEntityManagerFactory` and
 * `tenantEntityManagerFactory` instead. Without this exclusion, Spring fails to start.
 */
@SpringBootApplication(exclude = [JpaRepositoriesAutoConfiguration::class])
class TestApplication

/**
 * Simple controller used by [TenantInterceptorAutoConfigTest] to verify that the
 * [TenantInterceptor] correctly sets the tenant from JWT claims and clears it after the request.
 */
@RestController
class TestTenantController {
    /** Returns the current tenant ID, or blank if none is set. */
    @GetMapping("/tenant", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun getTenant(): String {
        return TenantContext.getTenantId()?.toString() ?: ""
    }

    /** Always throws — used to verify tenant context is cleared even on error. */
    @GetMapping("/tenant/error", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun error(): String {
        throw IllegalStateException("boom")
    }
}
