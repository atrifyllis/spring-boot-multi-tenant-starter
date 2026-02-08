package com.github.atrifyllis.multitenancy.autoconfigure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class MultiTenancyAutoConfigurationDisabledTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MultiTenancyAutoConfiguration::class.java))

    @Test
    fun `auto-config absent when explicitly disabled`() {
        contextRunner.withPropertyValues("multitenancy.enabled=false").run { ctx ->
            assertThat(ctx).doesNotHaveBean(MultiTenancyAutoConfiguration::class.java)
        }
    }
}
