// neuroassist-ai-elastic-stack-client-lib — Elasticsearch integration for NeuroAssist AI
//
// This Gradle module provides:
//   • Hexagonal ports: SearchPort, IndexPort (no ES imports in the port layer)
//   • ElasticsearchAdapter — implements both ports via the native ES Java client
//   • ElasticsearchClientConfig — Spring bean for co.elastic.clients.ElasticsearchClient
//   • ElasticsearchErrorDecoder — translates ES exceptions into ElasticException
//
// Consumed by any service that needs search or indexing capabilities. Services
// declare an `implementation` dependency on `:services:elastic-stack-client-lib`
// and depend on the ports (SearchPort / IndexPort) — never on the adapter or client directly.
//
// Update flow:
//   1. Change `elasticsearch_version` in project-context.json
//   2. Re-render this file: python scripts/render.py --template ...
//   3. Run: ./gradlew :services:elastic-stack-client-lib:build
//   4. Validate against the running Docker stack in compose-elastic.yml
//
// D-268: co.elastic.clients (native) — not Feign
// D-270: elastic-stack-client-lib module

plugins {
    `java-library`
    id("org.springframework.boot") version "3.5.0" apply false
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.neuroassist.elastic"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    // ── Hexagonal port layer ──────────────────────────────────────────────────
    api("org.springframework:spring-context")

    // ── Elasticsearch native Java client (D-268) ──────────────────────────────
    //
    // co.elastic.clients:elasticsearch-java is the official typed Java client.
    // It bundles its own low-level REST client transport; the explicit
    // elasticsearch-rest-client dependency pins the version to match ES server.
    implementation("co.elastic.clients:elasticsearch-java:9.4.1")
    implementation("org.elasticsearch.client:elasticsearch-rest-client:9.4.1")

    // ── JSON serialization (required by the native client) ────────────────────
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    // jakarta.json-api is the JSON-B SPI required by the ES client transport
    implementation("jakarta.json:jakarta.json-api:2.1.3")

    // ── Spring Web (RestClient / RestTemplate utilities, HTTP host parsing) ────
    implementation("org.springframework:spring-web")

    // ── Lombok ────────────────────────────────────────────────────────────────
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // ── Test ──────────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Testcontainers: optional, for integration tests hitting a real ES node
    testImplementation("org.testcontainers:elasticsearch:1.20.0")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

// ── Test configuration ────────────────────────────────────────────────────────
tasks.named<Test>("test") {
    useJUnitPlatform()
    // Testcontainers needs Docker; skip if not available in CI
    jvmArgs("-Dtestcontainers.reuse.enable=true")
}
