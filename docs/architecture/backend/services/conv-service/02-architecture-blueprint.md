<!-- OUTPUT-SCHEMA
- System Boundary
- External Actors and Systems
- End-to-End Flows
- Internal Boundaries
- Cross-cutting Concerns
-->

# 02 — Architecture Blueprint

## System Boundary
`conv-service` is a **single independent microservice** (shared ADR: independent microservices), deployed
on its own, registered in Eureka (REG) and routed via Spring Cloud Gateway (GWY) at the normalized public
surface `/api/v1/conv/**` (shared ADR: public API surface). It is the **system of record for the speech
tree** (`NoArvoreFala`) and for the per-locale content/audio references, in its own schema-per-tenant. It
is **not** the system of record for: patient identity/session (ACS), the `Comando`/`engineFeatureRef`
vocabulary (PAC/GEST), audio synthesis or tree authoring (TREI), or audit storage (HIST). CONV **reads**
Elasticsearch through the shared `elastic-stack-client-lib` and **reads** the audio file referenced by an
ES/DB record from a local filesystem store; it never writes embeddings or audio at runtime (that is TREI's
job).

## External Actors and Systems
- **Paciente com sessão (actor).** Drives navigation and node selection through the CONV frontend lib
  (`conv`), carrying the ACS-issued patient-with-session JWT (scopes `conv.arvore.navegar` /
  `conv.no.selecionar`). Entry channel is agnostic: gesture via GEST **or** direct touch/mouse (ADR-014).
- **Cuidadora (actor, indirect).** Provisions/revokes the session in ACS and authors content in TREI —
  both outside CONV. Not a CONV runtime caller.
- **acs-service.** JWT issuer/validator for the patient session; owner of the immediate-revocation
  **blacklist** (ADR-012). CONV validates tokens against the shared realm and honors the blacklist.
- **Elasticsearch 9.4.1** (index `arvore-fala`). Semantic/kNN retrieval engine on the CONV runtime path
  (ADR-015). Accessed only via `SearchPort` (`elastic-stack-client-lib`).
- **pac-service / gest-service.** Own the shared `engineFeatureRef` vocabulary; CONV resolves it read-only
  to bind a recognized gesture to a tree node (ADR-013).
- **Audio filesystem store.** Local directory of pre-synthesized MP3/Opus files (ADR-017). CONV reads by
  reference; TREI writes.
- **hist-service.** Receives audit facts (node selected, navigation) synchronously.
- **trei-service (sibling, not a runtime dependency).** Writes tree content, embeddings and audio that CONV
  later serves. CONV and TREI share the schema/index/store but not the runtime call path.

## End-to-End Flows
**Golden path — assisted phrase selection:**
1. Patient (via GEST gesture or direct touch) triggers an intent in the `conv` frontend.
2. Frontend calls `GET /api/v1/conv/tree` (navigation) or `POST /api/v1/conv/search` (assisted), carrying
   the patient-session JWT and the active locale.
3. On search: CONV builds a kNN query and calls `SearchPort` against `arvore-fala` filtered by
   `pacienteId` + locale; ES returns the closest node(s).
4. Patient selects a node → `POST /api/v1/conv/nodes/{id}/select`.
5. CONV returns the node's phrase text + the **audio reference** for the active locale.
6. Frontend plays the cached audio; CONV emits `SpeechNodeSelected` to HIST synchronously.

**Alternative — manual navigation (and ES-down fallback, OPS-003):** steps 2–3 are served straight from
PostgreSQL (`parentId` walk) with no Elasticsearch call. Semantic search is simply unavailable; the tree
is fully walkable. CONV surfaces a `degraded: true` marker rather than failing.

**Alternative — audio missing / store down (OPS-004):** step 5 returns the text with a null/absent audio
reference; the frontend falls back to the browser Web Speech API (generic voice). Communication proceeds.

**Alternative — gesture→node binding:** a gesture recognized by GEST arrives with an `engineFeatureRef`;
CONV resolves it to the bound node (if any) and treats it as a selection, reusing the shared vocabulary
rather than a CONV-owned mapping.

**Alternative — revoked session:** any call whose JWT is on the ACS blacklist is rejected (401/403)
immediately, regardless of the token's non-expiry.

## Internal Boundaries
Hexagonal (shared ADR hexagonal), package base `com.neuroassist.conv`:
- **`api`** — REST controllers, request/response DTOs, bean-validation, RFC 7807 `ProblemDetail`. Two
  read surfaces: patient runtime (navigate/search/select) and an internal read surface for the shared
  vocabulary resolution.
- **`application`** — use cases (navigate tree, assisted search, select node, resolve gesture binding),
  the locale-resolution policy, and the **degradation policy** (ES-down → static; audio-absent → signal
  fallback). Depends on domain + outbound ports only.
- **`domain`** — `ArvoreFala` / `NoArvoreFala` aggregate; `Locale`, `ConteudoLocalizado`, `AudioRef`,
  `EngineFeatureRef` value objects; navigation and locale-availability invariants. Pure Java.
- **`infrastructure`** — JPA entities/repositories (schema-per-tenant), inbound REST adapters, and outbound
  port adapters: the **Elasticsearch retrieval adapter over `SearchPort`**, `AcsSessionPort` (token/scope +
  blacklist check), `VocabularyPort` (resolve `engineFeatureRef` from PAC/GEST), `AudioRefPort` (map a node
  to its filesystem audio reference), `HistAuditPort` (synchronous audit). CONV imports
  `elastic-stack-client-lib`; it does not import the ES client directly.

## Cross-cutting Concerns
- **Security / identity.** CONV is a pure **resource server** for the patient-with-session actor (ADR-012):
  validate the ACS JWT, enforce scopes `conv.arvore.navegar` / `conv.no.selecionar`, and **honor the ACS
  blacklist** for immediate revocation of the non-expiring session. No caregiver/admin authority is ever
  accepted on these tokens. Service-to-service reads (vocabulary) reuse the OAuth2 `client_credentials`
  pattern already established for cross-service calls (see shared ADR edge-device-auth for the isolation
  approach).
- **Multi-tenancy.** Schema-per-tenant (empresa), `neuroassist_global` + per-empresa schemas, resolved the
  same way as the other services; every tree query is tenant- and patient-scoped.
- **Observability.** Standard pipeline event/trace conventions; every node selection is auditable in HIST
  as originating from the patient session (not the device token, not the caregiver).
- **Data sensitivity (LGPD, SEC-002).** The tree text and (upstream) the cloned-voice audio are sensitive
  health/biometric data; CONV treats index content and audio references under the same safeguards as
  REG-001/OPS-002. No image/landmark/biometric-capture data enters CONV at any boundary.
- **Internationalization (ADR-018).** Locale is a first-class dimension of every read: content and audio
  reference are resolved per the active session locale, independently per node.
- **Degradation as a contract, not an accident.** OPS-003/OPS-004 fallbacks are explicit application-layer
  policies with a surfaced `degraded` marker, not silent failures.
