package io.github.atrifyllis.multitenancy.autoconfigure

import io.github.atrifyllis.multitenancy.application.service.TenantInterceptor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Root autoconfiguration for multi-tenancy starter. Registers web interceptor when running in a
 * servlet web app and multitenancy is enabled.
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "multitenancy",
    name = ["enabled", "tenant-interceptor.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@EnableConfigurationProperties(MultitenancyProperties::class)
class MultiTenancyAutoConfiguration {
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(WebMvcConfigurer::class, TenantInterceptor::class)
    fun tenantInterceptor(): TenantInterceptor = TenantInterceptor()

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(WebMvcConfigurer::class, TenantInterceptor::class)
    fun tenantWebMvcConfigurer(tenantInterceptor: TenantInterceptor): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(tenantInterceptor)
            }
        }
}
