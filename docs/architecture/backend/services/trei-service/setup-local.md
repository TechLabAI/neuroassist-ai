# TREI — Local Setup (`trei-service`)

> Stack: Java 21 / Spring Boot · Gradle (Kotlin DSL) · resolved `BACKEND_LANGUAGE = java`.

## HTTP port

`trei-service` listens on **`8095`** locally (internal `/trei`; edge `/api/v1/trei` via GWY). This is the
port the scaffold's `generator-config.yml` / `application.yml` declares.

## Local dependencies

| Dependency | Purpose | Notes |
|---|---|---|
| PostgreSQL | authoring state (schema-per-`empresa`) + writes to shared `conv_*` tree tables | migrations shipped to **MIG**; TREI runs no local Flyway |
| Elasticsearch 9.4.1 | writes `neuroassist-ai_arvore-fala` via `IndexPort` | shared with CONV; `dynamic:strict`, `embedding` dims 768 cosine |
| Embedding sidecar | self-hosted multilingual sentence-transformers (index-time vector) | `TREI_EMBED_MODEL_ID` = **G5 anchor** |
| Audio filesystem | cached TTS output CONV reads | `TREI_AUDIO_ROOT` MUST equal `CONV_AUDIO_ROOT` |
| ACS (`acs-service`) | OAuth2 realm (caregiver + patient-with-session) | resource server; fail-closed |
| PAC (`pac-service`) | patient resolution + opaque `engine_feature_ref` | read-only |
| HIST (`hist-service`) | audit facts | synchronous, buffered by outbox |
| REG (Eureka) / GWY | discovery / edge routing | platform-internal Eureka |
| Anthropic API | LLM authoring | authoring only, off runtime |
| ElevenLabs API | cloned-voice TTS | once per (node, locale) |

## Environment variables

```bash
# --- G5 (must match CONV) ---
export TREI_EMBED_MODEL_ID="multilingual-e5-<ver>"   # MUST equal CONV_EMBED_MODEL_ID (data-model §3)
export TREI_EMBED_SIDECAR_URL="http://localhost:8091/embed"

# --- shared stores (must match CONV conventions) ---
export TREI_AUDIO_ROOT="/var/neuroassist/audio"      # MUST equal CONV_AUDIO_ROOT
export TREI_ES_INDEX="neuroassist-ai_arvore-fala"

# --- third-party (authoring only) ---
export ANTHROPIC_API_KEY="…"
export ANTHROPIC_MODEL_ID="claude-<model>-<version>"
export ELEVENLABS_API_KEY="…"
export ELEVENLABS_VOICE_ID="<per-patient cloned voice id>"

# --- platform ---
export ELASTICSEARCH_URIS="http://localhost:9200"
export ACS_JWK_SET_URI="http://localhost:8081/realms/neuroassist/protocol/openid-connect/certs"
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/neuroassist"
```

> **G5 boot check.** A startup smoke asserts `TREI_EMBED_MODEL_ID` is set and, when reachable, equals the
> sidecar's reported runtime model id; the deploy manifest additionally asserts
> `TREI_EMBED_MODEL_ID == CONV_EMBED_MODEL_ID` (SPEC-11). A mismatch is a **deploy error**, not a silent
> runtime state.

## Build & run (java)

```bash
./gradlew :trei-service:build      # install deps + compile + test
./gradlew :trei-service:bootRun    # run locally on :8095
./gradlew :trei-service:test       # tests (unit + Cucumber BDD)
```

> **Test config (Learning D-492).** `application-test.yml` sets
> `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (static test JWKS), **not** `issuer-uri`
> (avoids OIDC discovery against a Keycloak that is not up in tests). No `@EmbeddedKafka` is needed
> (`messaging.enabled = false`).
