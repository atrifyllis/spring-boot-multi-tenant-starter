package io.github.atrifyllis.multitenancy.autoconfigure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class MultiTenancyAutoConfigurationEnabledTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MultiTenancyAutoConfiguration::class.java))
            .withPropertyValues(
                "multitenancy.tenant-jpa-base-package=io.github.atrifyllis.multitenancy",
                "multitenancy.admin-jpa-packages-to-scan=io.github.atrifyllis.multitenancy.admin",
            )

    @Test
    fun `auto-config present when enabled property missing (defaults to true)`() {
        contextRunner.run { ctx ->
            assertThat(ctx).hasSingleBean(MultiTenancyAutoConfiguration::class.java)
        }
    }

    @Test
    fun `auto-config present when explicitly enabled`() {
        contextRunner.withPropertyValues("multitenancy.enabled=true").run { ctx ->
            assertThat(ctx).hasSingleBean(MultiTenancyAutoConfiguration::class.java)
        }
    }
}
