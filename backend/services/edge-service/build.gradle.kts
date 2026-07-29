plugins {
    id("org.springframework.boot") version "3.5.11"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "6.25.0"
    java
}

group = "com.neuroassist"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories { mavenCentral() }

extra["springCloudVersion"] = "2025.0.1"

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
}
}

dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Persistence (schema gerenciado pelo neuroassist-ai-migrations)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // Messaging (D-490: spring-boot-starter-kafka does not exist; correct artifact is spring-kafka)
    implementation("org.springframework.boot:spring-boot-starter-artemis")

    // Service Discovery + HTTP Clients
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("io.github.resilience4j:resilience4j-spring-boot3")

    // Observability
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")
// Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    // ActiveMQ: Artemis embedded (spring.artemis.mode=embedded) — sem testcontainers:activemq
    testImplementation("org.apache.activemq:artemis-jakarta-server:2.31.2")


    // BDD — Cucumber
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.18.0")
    testImplementation("io.cucumber:cucumber-java:7.18.0")
    testImplementation("io.cucumber:cucumber-spring:7.18.0")
    testImplementation("org.junit.platform:junit-platform-suite-api")
    testImplementation("org.junit.platform:junit-platform-suite-engine")

}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register("architectureCheck") {
    dependsOn(tasks.test)
    doFirst { logger.lifecycle("Running ArchUnit checks via :edge-service:test...") }
}

// Spotless — formatação canônica (google-java-format AOSP, 4 spaces)
// Rodar: ./gradlew :edge-service:spotlessApply
// Verificar: ./gradlew :edge-service:spotlessCheck
spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.22.0").aosp()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
