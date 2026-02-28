package com.github.atrifyllis.multitenancy

import com.github.atrifyllis.multitenancy.application.service.TenantContext
import com.github.atrifyllis.multitenancy.autoconfigure.AdminDataSourceAutoConfiguration
import com.github.atrifyllis.multitenancy.autoconfigure.RlsAutoConfiguration
import com.github.atrifyllis.multitenancy.autoconfigure.TenantJpaAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication(
    exclude =
        [
            DataSourceAutoConfiguration::class,
            HibernateJpaAutoConfiguration::class,
            TenantJpaAutoConfiguration::class,
            AdminDataSourceAutoConfiguration::class,
            RlsAutoConfiguration::class,
        ]
)
class TestApplication

@RestController
class TestTenantController {
    @GetMapping("/tenant", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun getTenant(): String {
        return TenantContext.getTenantId()?.toString() ?: ""
    }

    @GetMapping("/tenant/error", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun error(): String {
        throw IllegalStateException("boom")
    }
}
