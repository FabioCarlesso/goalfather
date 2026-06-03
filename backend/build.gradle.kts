import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"
    kotlin("plugin.jpa") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.carlesso"
version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    // ── Domain compila com kotlinx coroutines + serialization (annotations).
    //    Spring NÃO é importado em domain/ por convencao — verificado em CI no futuro.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // ── Spring Boot 3.4 (Spring Framework 6.2, Servlet 6, JVM 21)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // ── Auth (issue #18): Spring Security + JWT (jjwt) ────────────────────
    //    BCrypt vem do starter-security. jjwt é dividido em api/impl/jackson:
    //    só a api é compile-time; impl/jackson resolvem em runtime.
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Cache: abstração do Spring + Caffeine como provider (issue #13).
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Kotlin support no Spring
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Persistencia: H2 (dev/test) + PostgreSQL (profile prod)
    implementation("org.flywaydb:flyway-core")
    // Flyway 10 modularizou o suporte por banco: o core sozinho lanca
    // "Unsupported Database: PostgreSQL" no profile prod. Modulo dedicado:
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")

    // ── Testes ───────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        // mockito-core conflita com mockk em algumas configs; excluido por enquanto
        exclude(module = "mockito-core")
    }
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.springframework.security:spring-security-test")
}

// Plugin JPA precisa de no-arg constructors para entities — o kotlin-jpa plugin
// aplica isso automaticamente. Para @Embeddable/value classes ainda precisamos
// abrir as classes (kotlin-spring já cobre @Service/@Component).

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

// Spring Boot 3.4 + Servlet container embarcado (Tomcat) por padrao
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("goalfather.jar")
    mainClass.set("com.carlesso.goalfather.GoalfatherApplicationKt")
}
