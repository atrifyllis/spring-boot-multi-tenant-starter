package io.github.atrifyllis.multitenancy.autoconfigure

import com.zaxxer.hikari.HikariDataSource
import io.github.atrifyllis.multitenancy.adapters.secondary.persistence.TenantAwareDataSource
import jakarta.persistence.EntityManagerFactory
import javax.sql.DataSource
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizers
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.transaction.PlatformTransactionManager

/**
 * Autoconfigures the tenant (application) DataSource and JPA stack. Mirrors core DataSourceConfig
 * with minimal differences and safe conditionals.
 *
 * Note: This configuration does NOT enable JPA repositories scanning. The consuming application
 * should add @EnableJpaRepositories in their own configuration:
 *
 * @EnableJpaRepositories( entityManagerFactoryRef = "tenantEntityManagerFactory",
 *   transactionManagerRef = "transactionManager", basePackages = ["com.yourapp.repositories"] )
 */
@EnableConfigurationProperties(MultitenancyProperties::class)
@AutoConfiguration
@ConditionalOnClass(LocalContainerEntityManagerFactoryBean::class, JpaTransactionManager::class)
@ConditionalOnProperty(
    prefix = "multitenancy",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class TenantJpaAutoConfiguration(private val multitenancyProperties: MultitenancyProperties) {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "multitenancy.tenant.datasource")
    @ConditionalOnMissingBean(name = ["tenantDataSourceProperties"])
    fun tenantDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "multitenancy.tenant.datasource.hikari")
    @ConditionalOnMissingBean(name = ["tenantDataSource"])
    fun tenantDataSource(props: DataSourceProperties): DataSource {
        val dataSource =
            props.initializeDataSourceBuilder().type(HikariDataSource::class.java).build()
        return TenantAwareDataSource(dataSource)
    }

    @Bean(name = ["transactionManager"])
    @Primary
    @ConditionalOnMissingBean(name = ["transactionManager"])
    fun transactionManager(
        transactionManagerCustomizers: ObjectProvider<TransactionManagerCustomizers>,
        emf: EntityManagerFactory,
    ): PlatformTransactionManager {
        val transactionManager = JpaTransactionManager(emf)
        transactionManagerCustomizers.ifAvailable { customizers ->
            customizers.customize(transactionManager)
        }
        return transactionManager
    }

    @Bean(name = ["tenantEntityManagerFactory"])
    @Primary
    @ConditionalOnMissingBean(name = ["tenantEntityManagerFactory"])
    fun tenantEntityManagerFactory(
        builder: EntityManagerFactoryBuilder,
        dataSource: DataSource,
    ): LocalContainerEntityManagerFactoryBean {
        return builder
            .dataSource(dataSource)
            .packages(multitenancyProperties.tenantJpaBasePackage)
            .persistenceUnit("tenantDatasource")
            .build()
    }
}
