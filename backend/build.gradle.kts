// Root build.gradle.kts for neuroassist-ai backend
// TODO[lote-6a]: render from templates/operational/infra/backend-build.gradle.kts.j2

plugins {
    java
}

allprojects {
    group = "com.neuroassist"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
}
// Spring Boot 3.5.0 expected when sub-modules apply the plugin.
