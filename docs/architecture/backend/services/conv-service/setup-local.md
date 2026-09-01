# CONV — Local Setup (`conv-service`)

> Stack: Java 21 / Spring Boot 3.5.x / Gradle (Kotlin DSL). Package base `com.neuroassist.conv`.

## Local dependencies

| Dependency | Why | Posture |
|---|---|---|
| PostgreSQL 15+ | authoritative tree store (schema-per-tenant) | required |
| Elasticsearch 9.4.1 | semantic kNN search (`neuroassist-ai_arvore-fala`) | optional locally — CONV degrades to static navigation (OPS-003) |
| Audio filesystem dir | cached voice playback references (`CONV_AUDIO_ROOT`) | optional locally — degrades to Web Speech (OPS-004) |
| REG (Eureka) + GWY | discovery + `/api/v1/conv/**` routing | required for cross-service/e2e |
| ACS (`acs-service`) | patient-session JWT validation + blacklist | required (hard dependency) |
| MIG (`neuroassist-ai-migrations`) | provisions CONV tables per tenant schema | required (Flyway) |

## HTTP port

`conv-service` listens on **`8090`** locally (declared here; the scaffold `generator-config.yml` reads
the same value). Public access is via GWY at `/api/v1/conv/**`.

## Environment variables

| Var | Example | Notes |
|---|---|---|
| `SERVER_PORT` | `8090` | local HTTP port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/neuroassist` | schema-per-tenant resolved at runtime |
| `ELASTICSEARCH_HOST` / `ELASTICSEARCH_PORT` | `localhost` / `9200` | from `elastic-stack-client-lib` config |
| `CONV_ES_INDEX` | `neuroassist-ai_arvore-fala` | search index |
| `CONV_EMBED_MODEL_ID` | `<multilingual-embed-model>` | ES inference model for `query_vector_builder`; MUST match TREI index-time model |
| `CONV_AUDIO_ROOT` | `/var/neuroassist/audio` | cached audio store root |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | `http://localhost:8081/realms/neuroassist/protocol/openid-connect/certs` | shared Keycloak realm; **use `jwk-set-uri`, not `issuer-uri`, in tests (D-492)** |
| `ACS_BASE_URL` | `http://acs-service` | blacklist / session checks (via discovery in cluster) |

## Commands (Java / Gradle)

```bash
./gradlew :conv-service:build      # install deps + compile
./gradlew :conv-service:bootRun    # run locally on :8090
./gradlew :conv-service:test       # unit + BDD (Cucumber) tests
```

> **Test config note (Learning D-492):** `src/test/resources/application-test.yml` MUST configure
> `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` with a static test JWKS. `issuer-uri` triggers
> OIDC discovery at boot against a Keycloak that is not running in tests and fails JPA init.
> `messaging.enabled = false`, so **no** `@EmbeddedKafka` is needed for the Cucumber Spring context.
