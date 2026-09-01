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
- **Language / runtime:** Java 21.
- **Framework:** Spring Boot 3.5.x (Web, Validation, Data JPA, OAuth2 Resource Server).
- **Persistence:** PostgreSQL 15+, schema-per-tenant (`neuroassist_global` + per-empresa schemas).
- **Search / retrieval:** Elasticsearch 9.4.1 via the shared `elastic-stack-client-lib` (`SearchPort` /
  `IndexPort`) — index `arvore-fala`, semantic kNN over `dense_vector` (ADR-015). **Runtime is
  retrieval-only.**
- **Migrations:** centralized Flyway via the `neuroassist-ai-migrations` (MIG) service — CONV does not run
  its own migrator (see doc 05).
- **Service discovery / routing:** Eureka (REG) + Spring Cloud Gateway (GWY), public surface
  `/api/v1/conv/**`.
- **Messaging:** none — `stack.messaging.enabled = false`; all inter-service communication is synchronous
  REST.
- **AI:** none — `ai.hasAi = false`. CONV runtime never calls an LLM (ADR-015) nor the ElevenLabs API
  (ADR-017); both belong to the TREI module.
- **Audio:** local filesystem store of pre-synthesized MP3/Opus; CONV reads by reference only (ADR-017).

## Service Boundary
`conv-service` is a **single independent microservice** (shared ADR: independent microservices), deployed
on its own, registered in Eureka and routed via GWY. It is the **system of record for the speech tree**
(`NoArvoreFala` + per-locale content/audio references). It is intentionally **not** decomposed further:
navigation, assisted search and node selection share the same aggregate, transactions and tenant/patient
context; splitting them would add network hops without a bounded-context seam.

It is **new** (ADR-013): a retrofit deployable, **not** an extension of `pac-service`. It deliberately does
not touch PAC's production schema — coupling to PAC/GEST is limited to the shared `engineFeatureRef`
vocabulary. TREI is a **sibling** module that writes the same schema/index/store CONV reads; they share
data, not a runtime call path.

## Package Organization
Hexagonal (shared ADR hexagonal), package base `com.neuroassist.conv`:

```
src/main/java/com/neuroassist/conv/
+-- api/              REST controllers, request/response DTOs, mappers, RFC 7807 error handling
+-- application/      use-case services (navigate, assisted-search, select, resolve-binding),
                      locale-resolution policy, degradation policy (@Transactional lives here)
+-- domain/           ArvoreFala/NoArvoreFala aggregate, value objects (ConteudoLocalizado, AudioRef,
                      EngineFeatureRef, Locale), ports (interfaces), invariants — no Spring/JPA imports
+-- infrastructure/   JPA entities/repositories (schema-per-tenant), inbound REST adapters, and outbound
                      port adapters: Elasticsearch retrieval (over SearchPort), AcsSessionPort,
                      VocabularyPort, AudioRefPort, HistAuditPort, config
```

Dependency rule (shared ADR hexagonal): `domain` imports nothing outward; `application` depends on
`domain`; the inbound API layer and `infrastructure` depend inward. The Elasticsearch client is never
imported directly by CONV — only through `elastic-stack-client-lib`'s `SearchPort`/`IndexPort`, whose
adapter (`ElasticsearchAdapter`) is the sole holder of `co.elastic.clients`.

## Hub Service Dependencies
- **TechLab AI:** none. CONV consumes no orchestration, knowledge, document_composition or analytics
  capability. The whole point of ADR-015 is that runtime is retrieval-only — meaning comes from curated
  content, not inference. (Generative assistance, if any, is confined to the TREI authoring phase, not
  CONV.)

## External Integrations
- **Elasticsearch (`elastic-stack-client-lib`)** — `SearchPort`: assisted semantic search (kNN) over
  `arvore-fala`, filtered by `pacienteId` + active locale; returns the closest existing node(s). CONV
  reads only. **Open item (doc 06):** kNN is a top-level `knn` search clause; the current
  `SearchPort.search(indexPattern, Map query)` passes a Query-DSL map — specs must confirm whether the map
  can carry `knn` or `SearchPort` needs a kNN-aware method. Indexing/embedding is TREI's write path via
  `IndexPort` (incremental per-node upsert, ADR-015), not CONV's.
- **ACS (`acs-service`)** — `AcsSessionPort`: validate the patient-with-session JWT, enforce scopes
  `conv.arvore.navegar` / `conv.no.selecionar`, and check the ACS **blacklist** for immediate revocation
  of the non-expiring session (ADR-012). Shared Keycloak realm. Synchronous REST / resource-server config.
- **PAC / GEST (`pac-service` / `gest-service`)** — `VocabularyPort`: resolve the shared `engineFeatureRef`
  vocabulary so a recognized gesture binds to a tree node (ADR-013). Read-only, synchronous REST by
  reference; no schema coupling.
- **Audio filesystem store** — `AudioRefPort`: map a node+locale to its cached audio reference and confirm
  presence (drives the OPS-004 fallback decision). CONV reads; TREI writes.
- **HIST (`hist-service`)** — `HistAuditPort`: send `SpeechNodeSelected` / `PhraseMatched` audit facts
  synchronously.
- **No third-party API on the CONV runtime path** — no LLM, no ElevenLabs, no biometric/image ingress.

## Async vs Sync Decisions
- **All cross-service calls are synchronous REST** (messaging disabled). Discovery via Eureka, routing via
  GWY.
- **Retrieval is a soft dependency (fail-open to static):** if Elasticsearch is unreachable or the semantic
  half is unavailable, CONV serves the tree statically from PostgreSQL and surfaces `degraded: true`
  (OPS-003) — it must **not** fail the navigation path. This mirrors the platform's degradation stance
  (semantic → lexical/static fallback with an explicit degraded marker).
- **Audio is a soft dependency (fail-open to Web Speech):** a missing/unreachable audio reference returns
  text with a null audio ref; the frontend uses the browser Web Speech API (OPS-004).
- **Identity/revocation is a hard dependency (fail-closed):** a token that is invalid, out-of-scope, or on
  the ACS blacklist is rejected. Revocation of the non-expiring session must take effect immediately.
- **Audit to HIST is synchronous** but best-effort for availability: a HIST outage must not block the
  patient's ability to communicate (selection succeeds; the audit write is retried/queued locally). Exact
  policy is deferred to specs.
- **Reevaluation trigger:** if the pilot grows beyond one patient/edge to institutional tenants, moving
  audit/indexing signals behind a broker (RabbitMQ) is the first candidate — recorded as a known future
  decision, not MVP work (consistent with the project-wide messaging note).
