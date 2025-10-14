package io.github.atrifyllis.multitenancy.autoconfigure

import io.github.atrifyllis.multitenancy.adapters.secondary.persistence.TenantAwareDataSource
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.transaction.PlatformTransactionManager
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class TenantJpaAutoConfigurationTest {

    companion object {
        private val postgresContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:alpine3.19")
                .withTmpFs(mapOf("/var/lib/postgresql/data" to "rw"))
                .withUsername("core")
                .withPassword("core")
                .withCommand("postgres", "-c", "fsync=off", "-c", "log_statement=all")
                .withReuse(true)

        init {
            postgresContainer.start()
        }
    }

    private fun defaultRunner(): ApplicationContextRunner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    DataSourceAutoConfiguration::class.java,
                    DataSourceTransactionManagerAutoConfiguration::class.java,
                    HibernateJpaAutoConfiguration::class.java,
                    TenantJpaAutoConfiguration::class.java,
                ),
            )
            .withPropertyValues(
                "multitenancy.enabled=true",
                "multitenancy.tenant-jpa-base-package=io.github.atrifyllis.multitenancy",
                "multitenancy.admin-jpa-packages-to-scan=io.github.atrifyllis.multitenancy.admin",
                "multitenancy.tenant.datasource.url=${postgresContainer.jdbcUrl}",
                "multitenancy.tenant.datasource.username=${postgresContainer.username}",
                "multitenancy.tenant.datasource.password=${postgresContainer.password}",
                "spring.flyway.enabled=false",
            )

    @Test
    fun `creates tenant DataSource, EMF and TX manager when enabled`() {
        defaultRunner().run { ctx ->
            assertThat(ctx).hasBean("tenantDataSource")
            val ds = ctx.getBean("tenantDataSource", DataSource::class.java)
            assertThat(ds).isInstanceOf(TenantAwareDataSource::class.java)

            assertThat(ctx).hasBean("tenantEntityManagerFactory")
            assertThat(ctx).hasSingleBean(LocalContainerEntityManagerFactoryBean::class.java)

            assertThat(ctx).hasBean("transactionManager")
            assertThat(ctx).hasSingleBean(PlatformTransactionManager::class.java)
        }
    }

    @Test
    fun `backs off when tenantDataSource already provided`() {
        defaultRunner().withUserConfiguration(UserDsConfig::class.java).run { ctx ->
            // ours should back off and the provided one should exist (and not be wrapped)
            val ds = ctx.getBean("tenantDataSource", DataSource::class.java)
            assertThat(ds).isNotInstanceOf(TenantAwareDataSource::class.java)
        }
    }

    @Test
    fun `does not create JPA beans when JPA not on classpath`() {
        defaultRunner()
            .withClassLoader(
                FilteredClassLoader(LocalContainerEntityManagerFactoryBean::class.java),
            )
            .run { ctx ->
                // The entire TenantJpaAutoConfiguration should back off when JPA is missing
                assertThat(ctx).doesNotHaveBean("tenantDataSource")
                assertThat(ctx).doesNotHaveBean("tenantEntityManagerFactory")
                // A transactionManager bean may exist (from DataSourceTransactionManagerAutoConfiguration)
                // but it should NOT be a JpaTransactionManager
                if (ctx.containsBean("transactionManager")) {
                    val tx = ctx.getBean("transactionManager")
                    assertThat(tx).isNotInstanceOf(JpaTransactionManager::class.java)
                }
            }
    }

    @Test
    fun `disabled when multitenancy is off`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantJpaAutoConfiguration::class.java))
            .withPropertyValues("multitenancy.enabled=false")
            .run { ctx ->
                assertThat(ctx.containsBean("tenantDataSource")).isFalse()
                assertThat(ctx.containsBean("tenantEntityManagerFactory")).isFalse()
                assertThat(ctx.containsBean("transactionManager")).isFalse()
            }
    }

    @Configuration
    private class UserDsConfig {
        @Bean(name = ["tenantDataSource"])
        fun userProvidedTenantDataSource(): DataSource =
            org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:userds;DB_CLOSE_DELAY=-1",
                "sa",
                "",
            )
    }
}
