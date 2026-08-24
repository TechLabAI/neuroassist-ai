// e2e-tests — NAO e um microservico.
//
// Este modulo nao tem `src/main`, nao aplica o plugin do Spring Boot e nao produz
// artefato executavel. Ele existe so para hospedar a suite E2E que roda por HTTP
// contra o stack ja de pe (docker-compose.dev.yml), exatamente como um cliente
// externo faria — nenhum bean da aplicacao e carregado em processo.
//
// Por que nao entra no `build` padrao: a suite exige os 7 servicos no ar. Quem
// clona o repo sem Docker precisa continuar rodando `./gradlew build` verde.
// A separacao e feita por tag JUnit (`e2e`):
//   - `test`    -> exclui a tag  => nenhum teste roda, `build` fica verde sem stack
//   - `e2eTest` -> inclui a tag  => a suite de verdade, invocada explicitamente
plugins {
    id("com.diffplug.spotless") version "6.25.0"
    java
}

group = "com.neuroassist"

version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

dependencies {
    testImplementation("io.rest-assured:rest-assured:5.5.0")
    testImplementation("io.rest-assured:json-path:5.5.0")
    testImplementation("org.assertj:assertj-core:3.26.3")

    // Serializador do RestAssured. Os cenarios originais montavam o corpo como texto JSON e nao
    // precisavam dele; a FixtureFactory monta corpos a partir de Map, e sem um provider no
    // classpath o RestAssured falha em tempo de execucao com "no JSON serializer found".
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")

    // SLF4J com binding simples — a regra do projeto proibe System.out, e aqui nao ha
    // contexto Spring para fornecer o Logback.
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

// `test` fica deliberadamente vazio: a tag `e2e` cobre 100% da suite.
tasks.test {
    useJUnitPlatform { excludeTags("e2e") }
}

// Ponto de entrada real:  ./gradlew :services:e2e-tests:e2eTest
val e2eTest by
        tasks.registering(Test::class) {
            group = "verification"
            description = "Suite E2E cross-service — exige o stack de dev no ar."
            testClassesDirs = sourceSets["test"].output.classesDirs
            classpath = sourceSets["test"].runtimeClasspath
            useJUnitPlatform { includeTags("e2e") }

            // Sem cache: o resultado depende do estado do stack, nao das entradas Gradle.
            outputs.upToDateWhen { false }

            // Repassa a configuracao de host/credenciais para o JVM de teste.
            listOf(
                            "e2e.acs.url",
                            "e2e.pac.url",
                            "e2e.gest.url",
                            "e2e.edge.url",
                            "e2e.hist.url",
                            "e2e.evnt.url",
                            "e2e.dash.url",
                            "e2e.login",
                            "e2e.senha",
                            "e2e.tenant",
                            "e2e.requireStack")
                    .forEach { key -> System.getProperty(key)?.let { systemProperty(key, it) } }

            testLogging {
                events("passed", "skipped", "failed")
                showStandardStreams = true
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.22.0").aosp()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
