package io.github.atrifyllis.multitenancy.autoconfigure

import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.transaction.PlatformTransactionManager

class AdminDataSourceAutoConfigurationTest {

    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AdminDataSourceAutoConfiguration::class.java))
            .withPropertyValues(
                "multitenancy.enabled=true",
                "multitenancy.tenant-jpa-base-package=io.github.atrifyllis.multitenancy",
                "multitenancy.admin-jpa-packages-to-scan=io.github.atrifyllis.multitenancy.admin",
                "multitenancy.admin.datasource.url=jdbc:h2:mem:admintest;DB_CLOSE_DELAY=-1",
                "multitenancy.admin.datasource.username=sa",
                "multitenancy.admin.datasource.password=",
            )

    @Test
    fun `adminDataSource is created and wrapped in TransactionAwareDataSourceProxy`() {
        contextRunner.run { ctx ->
            val ds = ctx.getBean("adminDataSource", DataSource::class.java)
            assertThat(ds).isInstanceOf(TransactionAwareDataSourceProxy::class.java)
            assertThat(ctx).hasSingleBean(DataSource::class.java)
        }
    }

    @Test
    fun `jOOQ beans present when jOOQ on classpath`() {
        contextRunner.run { ctx ->
            assertThat(ctx.containsBean("dataSourceConnectionProvider")).isTrue()
        }
    }

    @Test
    fun `jOOQ beans back off when jOOQ not on classpath`() {
        contextRunner.withClassLoader(FilteredClassLoader("org.jooq")).run { ctx ->
            assertThat(ctx.containsBean("dataSourceConnectionProvider")).isFalse()
        }
    }

    @Test
    fun `JPA beans present when JPA on classpath`() {
        contextRunner.run { ctx ->
            assertThat(ctx.containsBean("adminEntityManagerFactory")).isTrue()
            assertThat(ctx.containsBean("adminTransactionManager")).isTrue()
            assertThat(ctx.getBean(PlatformTransactionManager::class.java)).isNotNull()
            assertThat(ctx.getBean(LocalContainerEntityManagerFactoryBean::class.java)).isNotNull()
        }
    }

    @Test
    fun `JPA beans back off when JPA not on classpath`() {
        contextRunner
            .withClassLoader(
                FilteredClassLoader(
                    LocalContainerEntityManagerFactoryBean::class.java,
                    PlatformTransactionManager::class.java,
                )
            )
            .run { ctx ->
                assertThat(ctx.containsBean("adminEntityManagerFactory")).isFalse()
                assertThat(ctx.containsBean("adminTransactionManager")).isFalse()
            }
    }

    @Test
    fun `admin disabled when URL missing`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AdminDataSourceAutoConfiguration::class.java))
            .withPropertyValues(
                "multitenancy.enabled=true",
                "multitenancy.tenant-jpa-base-package=io.github.atrifyllis.multitenancy",
                "multitenancy.admin-jpa-packages-to-scan=io.github.atrifyllis.multitenancy.admin"
            )
            .run { ctx ->
                assertThat(ctx.containsBean("adminDataSource")).isFalse()
                assertThat(ctx.containsBean("adminEntityManagerFactory")).isFalse()
                assertThat(ctx.containsBean("adminTransactionManager")).isFalse()
            }
    }
}
