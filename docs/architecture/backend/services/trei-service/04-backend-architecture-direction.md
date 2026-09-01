<!-- OUTPUT-SCHEMA
- Stack
- Service Boundary
- Package Organization
- Hub Service Dependencies
- External Integrations
- Async vs Sync Decisions
-->

# 04 — Backend Architecture Direction

## Stack

- **Language / runtime:** Java 21, Spring Boot (`spring-boot`), package base `com.neuroassist`.
- **Build:** Gradle (Kotlin DSL), one module `backend/services/trei-service` in the root
  `settings.gradle.kts`.
- **Persistence:** PostgreSQL, schema-per-tenant (`empresa`); JPA/Hibernate. Migrations are centralized
  in the `neuroassist-ai-migrations` (MIG) service via Flyway — TREI does not run its own Flyway.
- **Service discovery / edge:** Eureka (REG) + Spring Cloud Gateway (GWY), same as the rest of the mesh.
- **Security:** OAuth2 resource server on the shared ACS realm (validates both caregiver and
  patient-with-session JWTs).
- **Search index:** Elasticsearch 9.4.1 via the shared `elastic-stack-client-lib` (IndexPort) — write
  side of the `arvore-fala` index.
- **Assisted generation (authoring only):** Claude/Anthropic API (LLM) and ElevenLabs API (TTS), plus a
  self-hosted multilingual embedding sidecar.

## Service Boundary

**Single independent microservice** (`ADR-002-independent-microservices`). TREI is a new deployable — it
is **not** an extension of `conv-service`, `pac-service` or `dash-service` (ADR-016 "Relação com
módulos"). CONV is navigation/retrieval; DASH is a consultation panel; PAC owns patient/command catalog.
TREI is the only authoring plane and the only writer of the `arvore-fala` index + audio store that CONV
reads. No decomposition inside TREI for the MVP — one service, hexagonal internally.

## Package Organization

Java, package root `com.neuroassist.trei`:

```
src/main/java/com/neuroassist/trei/
+-- api/              REST controllers (DASH authoring surface + patient-shell approval surface),
|                     DTOs, mappers
+-- application/      use-case services: ConduzirSessaoTreinamento, RegistrarPropostaLlm,
|                     AprovarNoCuidadora, AprovarNoPaciente, PublicarNo (@Transactional lives here;
|                     the dual-authorization gate is enforced here)
+-- domain/           NoArvoreFala aggregate, ConteudoLocale, SessaoTreinamento, value objects,
|                     domain events, ports (LlmAuthoringPort, EmbeddingPort, TtsSynthesisPort,
|                     AudioStorePort, IndexPort, AcsSessionPort, HistAuditPort)
+-- infrastructure/   adapters: Anthropic client, ElevenLabs client, embedding sidecar client,
                      filesystem audio adapter, elastic-stack-client-lib IndexPort adapter,
                      ACS resource-server config + Feign, HIST Feign, JPA persistence,
                      local outbox for retryable external steps, config
```

Dependency rule (shared hexagonal ADR — resolve the file at
`docs/architecture/backend/adr/ADR-001-hexagonal-architecture.md`, do not hard-cite the number in
generated prose): `domain` imports nothing outward; `application` depends on `domain`; `api` and
`infrastructure` depend inward.

## Hub Service Dependencies

- **TechLab AI hub:** none (`ai.hasAi = false`). TREI's LLM dependency is the **direct** Claude/Anthropic
  API, not the TechLab AI orchestration hub. No orchestration/knowledge/document_composition/analytics
  capability is consumed.
- **Intra-mesh (by reference):**
  - **ACS (`acs-service`)** — hard/fail-closed: validate caregiver + patient JWT scopes; honor the
    revocation blacklist.
  - **HIST (`hist-service`)** — synchronous audit of proposal/approval/publication facts (SEC-002).
  - **PAC (`pac-service`)** — read-only resolution of patient scope and, where a proposed phrase binds to
    a gesture, the shared `engineFeatureRef` vocabulary (coordinated with CONV/GEST; confirm in specs).

## External Integrations

- **Claude / Anthropic API (LLM)** — authoring only (TECH-007). Concrete model id/version, prompt
  contract, token budget and cost controls are **deferred to specs** (this vision layer fixes the
  provider, not the model). Occasional use; must disclose AI interaction to the user.
- **ElevenLabs API (TTS)** — one synthesis per approved node per locale (TECH-008), patient's cloned
  voice; output cached as MP3/Opus 64–96 kbps. PHI caveat: never send clinical text (ADR-017 §research);
  prefer disabling third-party model-training on the voice data.
- **Self-hosted multilingual embedding sidecar** — sentence-transformers; computes the index-time vector.
  **G5 invariant:** its `model_id` MUST equal CONV's query-time model id or CONV recall degrades
  silently.
- **Elasticsearch 9.4.1 (`arvore-fala`)** — write side via `elastic-stack-client-lib` IndexPort; TREI
  writes `texto + embedding + audio_ref`; CONV reads. TREI owns the CONV↔TREI consistency contract.
- **Audio filesystem store** — writes `${AUDIO_ROOT}/<empresa>/<paciente_id>/<locale>/<no_id>.<ext>`, the
  same convention CONV reads (file presence is CONV's Web-Speech fallback signal, OPS-004).

## Async vs Sync Decisions

- **No message broker** (`messaging.enabled = false`). All intra-mesh calls (ACS, HIST, PAC) are
  synchronous REST over Eureka + Gateway.
- **Approvals are asynchronous *in wall-clock time* but not broker-driven.** The caregiver and patient
  approve from different shells at different moments; TREI models this as two independent writes to the
  same aggregate, with the publication pipeline **triggered synchronously** the moment the second
  approval flips the gate.
- **The publication pipeline (embed → synthesize → index) runs as a guarded in-process flow with a local
  outbox** for the retryable external steps (ElevenLabs, embedding sidecar, ES). The node is not marked
  `ativo` until all steps succeed, so CONV never observes a partial publication. This is the concrete
  design for ADR-016's TODO[arquiteto] ("dispatch embedding+TTS only when both flags are true, possibly
  via event/outbox") — realized as an outbox without a broker.
- **LLM calls are request-scoped** to the active authoring session; they never occur on any read path.
