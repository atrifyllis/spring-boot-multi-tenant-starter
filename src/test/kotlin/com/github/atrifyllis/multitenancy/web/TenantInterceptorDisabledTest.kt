package com.github.atrifyllis.multitenancy.web

import com.github.atrifyllis.multitenancy.BasePostgresTest
import com.github.atrifyllis.multitenancy.application.service.TenantInterceptor
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["multitenancy.enabled=false"])
class TenantInterceptorDisabledTest : BasePostgresTest() {
    @Autowired lateinit var applicationContext: ApplicationContext

    @Test
    fun `interceptor bean absent when disabled`() {
        Assertions.assertThat(applicationContext.getBeansOfType(TenantInterceptor::class.java))
            .isEmpty()
    }
}
