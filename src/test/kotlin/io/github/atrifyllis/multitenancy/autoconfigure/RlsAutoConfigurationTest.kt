package io.github.atrifyllis.multitenancy.autoconfigure

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class RlsAutoConfigurationTest {

    private fun runner(vararg props: String) =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RlsAutoConfiguration::class.java))
            .withPropertyValues(*props)

    @Test
    fun `customizer sets placeholders from properties`() {
        runner(
                "multitenancy.enabled=true",
                "multitenancy.rls.enabled=true",
                "multitenancy.rls.schema=public",
                "multitenancy.rls.tenant-column=tenant_id",
                "multitenancy.rls.policy-name=tenant_isolation_policy",
                "multitenancy.rls.exclude-tables=persisted_event,flyway_schema_history",
            )
            .run { ctx ->
                val customizer =
                    ctx.getBean(
                        org.springframework.boot.autoconfigure.flyway
                                .FlywayConfigurationCustomizer::class
                            .java
                    )
                val fc = Flyway.configure()
                customizer.customize(fc)
                val placeholders = fc.placeholders
                assertThat(placeholders["rls_enabled"]).isEqualTo("true")
                assertThat(placeholders["rls_schema"]).isEqualTo("public")
                assertThat(placeholders["rls_tenant_column"]).isEqualTo("tenant_id")
                assertThat(placeholders["rls_policy_name"]).isEqualTo("tenant_isolation_policy")
                assertThat(placeholders["rls_exclude_tables"])
                    .isEqualTo("persisted_event,flyway_schema_history")
            }
    }

    @Test
    fun `auto-config backs off when disabled`() {
        runner("multitenancy.enabled=true", "multitenancy.rls.enabled=false").run { ctx ->
            assertThat(
                    ctx.getBeanNamesForType(
                        org.springframework.boot.autoconfigure.flyway
                                .FlywayConfigurationCustomizer::class
                            .java
                    )
                )
                .isEmpty()
        }
    }
}
