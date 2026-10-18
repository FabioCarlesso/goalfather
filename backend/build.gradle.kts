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

    // ── Observabilidade (issue #44) ───────────────────────────────────────
    //    Actuator já traz micrometer-core (a API de Meter/Timer/Counter, usada
    //    na camada application). O registry Prometheus é o backend concreto que
    //    expõe /actuator/prometheus no formato de scrape.
    implementation("io.micrometer:micrometer-registry-prometheus")

    // ── Auth (issue #18): Spring Security + JWT (jjwt) ────────────────────
    //    BCrypt vem do starter-security. jjwt é dividido em api/impl/jackson:
    //    só a api é compile-time; impl/jackson resolvem em runtime.
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // ── Swagger UI (revisão do PR #92) ──────────────────────────────────────
    //    Só a interface (webjar), sem springdoc: o contrato é escrito à mão e é
    //    a única fonte de verdade (CLAUDE.md). Gerar uma segunda spec a partir
    //    dos controllers abriria espaço para divergir dela. A página estática
    //    `static/swagger-ui.html` carrega o `contract/openapi.yaml`, copiado
    //    para dentro do jar pelo `processResources` abaixo. O locator-lite
    //    (versão gerida pelo Boot) deixa a página referenciar o webjar sem
    //    repetir a versão dele.
    implementation("org.webjars:swagger-ui:5.17.14")
    implementation("org.webjars:webjars-locator-lite")

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

    // Migrations validadas contra Postgres REAL (achado 4 da review do PR #47):
    // H2 em MODE=PostgreSQL não pega toda incompatibilidade de sintaxe. O teste
    // usa Testcontainers e se auto-desabilita sem Docker (roda no CI ubuntu).
    // Versões gerenciadas pelo BOM do Spring Boot.
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

// Plugin JPA precisa de no-arg constructors para entities — o kotlin-jpa plugin
// aplica isso automaticamente. Para @Embeddable/value classes ainda precisamos
// abrir as classes (kotlin-spring já cobre @Service/@Component).

tasks.test {
    useJUnitPlatform()
    // ContractDriftTest (issue #82) lê o contrato fora de backend/. Declarar
    // como input faz o Gradle re-rodar os testes quando só o YAML muda — sem
    // isso, `test` ficaria UP-TO-DATE e o drift passaria despercebido.
    inputs.file("../contract/openapi.yaml").withPathSensitivity(PathSensitivity.RELATIVE)
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

// O contrato entra no jar como `/openapi.yaml`, servido ao Swagger UI. Fonte
// única: o arquivo de `contract/`, nunca uma cópia versionada em resources.
// `inputs.file` faz o build FALHAR se o contrato não estiver lá (ex.: imagem
// Docker sem o contexto `contract`), em vez de publicar um Swagger vazio.
val contractFile = file("../contract/openapi.yaml")
tasks.processResources {
    inputs.file(contractFile).withPathSensitivity(PathSensitivity.RELATIVE)
    from(contractFile) { into("static") }
}

// Spring Boot 3.4 + Servlet container embarcado (Tomcat) por padrao
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("goalfather.jar")
    mainClass.set("com.carlesso.goalfather.GoalfatherApplicationKt")
}
