package io.github.atrifyllis.multitenancy.autoconfigure

import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import javax.sql.DataSource
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultConfiguration
import org.jooq.jpa.extensions.DefaultAnnotatedPojoMemberProvider
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer
import org.springframework.boot.autoconfigure.jooq.JooqProperties
import org.springframework.boot.autoconfigure.jooq.SpringTransactionProvider
import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizers
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.PlatformTransactionManager

/**
 * Autoconfigures admin DataSource + jOOQ + JPA, mirroring core with minimal conditions.
 *
 * ⚠️ SECURITY WARNING:
 * The beans configured here (especially jOOQ's DSLContext) BYPASS Row-Level Security (RLS).
 * They should ONLY be used for:
 * - Cross-tenant operations (admin features)
 * - Database migrations (Flyway)
 * - Internal library operations
 *
 * Regular application code should use JPA repositories which use the tenant datasource.
 */
@AutoConfiguration
@EnableConfigurationProperties(JooqProperties::class, MultitenancyProperties::class)
@ConditionalOnProperty(
    prefix = "multitenancy",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class AdminDataSourceAutoConfiguration {
    @Bean
    @ConfigurationProperties(prefix = "multitenancy.admin.datasource")
    @ConditionalOnMissingBean(name = ["adminDataSourceProperties"])
    //@Primary // TODO remove when tenant datasource is migrated

    fun adminDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    @Bean(name = ["adminDataSource"])
    //@Primary // TODO remove when tenant datasource is migrated
    @FlywayDataSource
    @ConditionalOnMissingBean(name = ["adminDataSource"])
    @ConditionalOnProperty(prefix = "multitenancy.admin.datasource", name = ["url"])
    fun adminDataSource(
        @Qualifier("adminDataSourceProperties") props: DataSourceProperties
    ): DataSource {
        val ds = props.initializeDataSourceBuilder().type(HikariDataSource::class.java).build()
        return TransactionAwareDataSourceProxy(ds)
    }

    // jOOQ wiring against admin DS
    /**
     * ⚠️ WARNING: Configures jOOQ to use admin datasource which BYPASSES RLS.
     * Do NOT inject DSLContext in regular services unless you explicitly need cross-tenant access.
     */
    @Bean
    @ConditionalOnClass(DefaultConfiguration::class)
    @ConditionalOnBean(name = ["adminDataSource"])
    @ConditionalOnMissingBean
    fun configurationCustomiser(
        @Qualifier("adminDataSource") admin: DataSource
    ): DefaultConfigurationCustomizer =
        DefaultConfigurationCustomizer { config: DefaultConfiguration ->
            config.setDataSource(admin)
            config.settings().withRenderFormatted(true)
            config.set(DefaultAnnotatedPojoMemberProvider())
        }

    /**
     * ⚠️ WARNING: This connection provider uses admin datasource which BYPASSES RLS.
     */
    @Bean
    @ConditionalOnClass(DefaultConfiguration::class)
    @ConditionalOnBean(name = ["adminDataSource"])
    @ConditionalOnMissingBean
    fun dataSourceConnectionProvider(
        @Qualifier("adminDataSource") admin: DataSource
    ): DataSourceConnectionProvider = DataSourceConnectionProvider(admin)

    // JPA wiring against admin DS
    @Bean(name = ["adminEntityManagerFactory"])
    @ConditionalOnClass(JpaTransactionManager::class, LocalContainerEntityManagerFactoryBean::class)
    @ConditionalOnBean(name = ["adminDataSource"])
    @ConditionalOnMissingBean(name = ["adminEntityManagerFactory"])
    fun adminEntityManagerFactory(
        @Qualifier("adminDataSource") admin: DataSource,
        multitenancyProperties: MultitenancyProperties
    ): LocalContainerEntityManagerFactoryBean {
        val emf = LocalContainerEntityManagerFactoryBean().apply { dataSource = admin }
        emf.setPackagesToScan(*multitenancyProperties.adminJpaPackagesToScan.split(",").map { it.trim() }.toTypedArray())
        emf.persistenceUnitName = "adminDatasource"
        emf.jpaVendorAdapter = HibernateJpaVendorAdapter()
        return emf
    }

    @Bean(name = ["adminTransactionManager"])
    @ConditionalOnClass(JpaTransactionManager::class, LocalContainerEntityManagerFactoryBean::class)
    @ConditionalOnBean(name = ["adminEntityManagerFactory"])
    @ConditionalOnMissingBean(name = ["adminTransactionManager"])
    fun adminTransactionManager(
        transactionManagerCustomizers: ObjectProvider<TransactionManagerCustomizers>,
        @Qualifier("adminEntityManagerFactory") emf: EntityManagerFactory,
    ): PlatformTransactionManager {
        val tx = JpaTransactionManager(emf)
        transactionManagerCustomizers.ifAvailable { it.customize(tx) }
        return tx
    }

    @Bean(name = ["adminTransactionProvider"])
    @ConditionalOnClass(JpaTransactionManager::class, LocalContainerEntityManagerFactoryBean::class)
    @ConditionalOnBean(name = ["adminTransactionManager"])
    @ConditionalOnMissingBean(name = ["adminTransactionProvider"])
    fun transactionProvider(
        @Qualifier("adminTransactionManager") txManager: PlatformTransactionManager
    ): SpringTransactionProvider = SpringTransactionProvider(txManager)
}
