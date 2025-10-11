plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinPluginSpring)
    alias(libs.plugins.kotlinPluginKapt)
    alias(libs.plugins.springDepManagement)
    alias(libs.plugins.ktfmt)
}

group = "io.github.atrifyllis"

version = "0.0.1-SNAPSHOT"

description = "spring-boot-multi-tenant-starter"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories { mavenCentral() }

// Use Spring Boot BOM for consistent dependency versions
dependencyManagement {
    imports {
        mavenBom(
            "org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}",
        )
    }
}

dependencies {
    api("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.kotlinLogging)
    // Web & Security are optional at runtime; beans created only if present
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    compileOnly("org.springframework.boot:spring-boot-starter-security")
    compileOnly(libs.jakartaPersistenceApi)
    compileOnly(libs.springJdbc)
    compileOnly(libs.byteBuddy)
    compileOnly(libs.hikari)
    compileOnly(libs.jooq)
    compileOnly(libs.jooqJpaExtensions)
    compileOnly(libs.springOrm)
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.flywaydb:flyway-core")
    compileOnly("org.flywaydb:flyway-database-postgresql")

    kapt("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")

    testImplementation(libs.jakartaPersistenceApi)
    testImplementation(libs.springJdbc)
    testImplementation(libs.byteBuddy)
    testImplementation(libs.hikari)
    testImplementation(libs.h2)
    testImplementation(libs.jooq)
    testImplementation(libs.jooqJpaExtensions)
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")

    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation(libs.testContainersPostgres)
    testImplementation("org.postgresql:postgresql")
}

kotlin { compilerOptions { freeCompilerArgs.addAll("-Xjsr305=strict") } }

tasks.withType<Test> { useJUnitPlatform() }

ktfmt { kotlinLangStyle() }
