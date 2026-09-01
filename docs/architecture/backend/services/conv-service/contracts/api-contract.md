# CONV — API Contract (`conv-service`)

> Module **CONV** · Service `conv-service` · Stack Java 21 / Spring Boot 3.5.x
> Public surface routed by GWY at `/api/v1/conv/**` (shared ADR `ADR-008-public-api-surface-normalization.md`).
> Security model: pure OAuth2 Resource Server for the *patient-with-session* actor
> (`ADR-003-identidade-paciente-sessao-consumida-do-acs.md` → shared decision ADR-012).
> Retrieval-only runtime (`ADR-002-retrieval-only-elasticsearch-searchport.md` → ADR-015).
> EARS acceptance criteria per [`../../../standards/ears-notation.md`](../../../standards/ears-notation.md) (D-057).

---

## 1. Conventions

- **Base path (internal):** `/conv`. **Public path (via GWY):** `/api/v1/conv`. All paths below are
  written relative to the public base `/api/v1/conv`.
- **Media type:** `application/json; charset=utf-8` for all request/response bodies.
- **Tenancy:** the `empresa` (tenant) and `pacienteId` are **derived from the validated JWT**, never
  taken from the request body or a query parameter. Every read is tenant- and patient-scoped by
  construction (schema-per-tenant).
- **Locale:** the *active locale* comes from the session (JWT claim `locale`, one of `pt` | `en`). It
  MAY be overridden per request by the `Accept-Language` header **only within the set of locales the
  patient's tree supports**; an unsupported override is rejected (see §6).
- **Versioning:** breaking changes introduce `/api/v2/conv/**` and keep `/api/v1` alive (shared
  ADR-008). Additive fields are non-breaking.
- **Idempotency:** all runtime endpoints are safe/idempotent reads except `POST …/select` and
  `POST …/search`, which are non-mutating on CONV state (they only emit audit facts) and therefore
  safe to retry — see [`data-model.md`](data-model.md) §Idempotency and SPEC-05.

### 1.1 Mandatory headers

| Header | Direction | Rule |
|---|---|---|
| `Authorization: Bearer <jwt>` | request | ACS-issued patient-with-session token; validated as resource server. |
| `X-Correlation-Id` | request/response | Injected by GWY when absent; **always echoed** on the response and propagated to downstream calls (ACS/PAC/GEST/HIST). |
| `Accept-Language` | request | Optional active-locale override; must resolve to a tree-supported locale. |
| `X-Degraded` | response | `true` when the response was served in a degraded mode (ES down → static, or audio absent). Mirrors the body `degraded` flag. |

**EARS — headers & envelope**

- THE SYSTEM SHALL expose exactly the endpoints in §2 under public base path `/api/v1/conv`.
- WHEN a request lacks `X-Correlation-Id`, THE SYSTEM SHALL accept the value injected by GWY and echo it on the response.
- THE SYSTEM SHALL derive `empresa` and `pacienteId` solely from the validated JWT and SHALL ignore any tenant/patient identifier present in the request body or query string.
- IF a request body or query contains image, video frame, landmark, or any biometric-capture data, THEN THE SYSTEM SHALL reject it with `400` (`type: …/no-biometric-ingress`). CONV never ingests biometric data (SEC-002 / LGPD).
- WHEN a breaking contract change is required, THE SYSTEM SHALL introduce `/api/v2/conv` and keep `/api/v1/conv` operational.

---

## 2. Resources

All endpoints require scopes as noted. Scopes come from the JWT (`ADR-003`): `conv.arvore.navegar`
(navigation/search) and `conv.no.selecionar` (node selection). No caregiver/admin authority is ever
accepted on these tokens.

### 2.1 `GET /api/v1/conv/tree` — fetch the navigable tree

Returns the patient's speech tree (structure + active-locale content). Structure is locale-invariant;
only content varies by locale (`ADR-005`).

- **Scope:** `conv.arvore.navegar`
- **Query params:** `rootOnly` (bool, default `false` — return only root + first level), `locale`
  (optional; overrides session locale within supported set).
- **200 response:**

```json
{
  "arvoreId": "b1e2…",
  "pacienteId": "p-1024",
  "localeRequested": "en",
  "localeServed": "en",
  "localesSuportados": ["pt", "en"],
  "degraded": false,
  "retrieval": "static",
  "nodes": [
    {
      "id": "n-root",
      "parentId": null,
      "tipo": "categoria",
      "ordem": 0,
      "engineFeatureRef": null,
      "conteudo": { "locale": "en", "texto": "Home", "contentStatus": "ativo",
                    "audioRef": { "present": true, "formato": "opus" }, "selecionavel": false }
    },
    {
      "id": "n-42",
      "parentId": "n-root",
      "tipo": "frase",
      "ordem": 3,
      "engineFeatureRef": "gest:blink-double",
      "conteudo": { "locale": "en", "texto": "I want water with ice", "contentStatus": "ativo",
                    "audioRef": { "present": true, "formato": "opus" }, "selecionavel": true }
    },
    {
      "id": "n-51",
      "parentId": "n-root",
      "tipo": "frase",
      "ordem": 4,
      "engineFeatureRef": null,
      "conteudo": { "locale": "en", "texto": null, "contentStatus": "pending_locale",
                    "audioRef": { "present": false }, "selecionavel": false }
    }
  ]
}
```

**Notes:** `retrieval` is `static` here (navigation is always PostgreSQL-backed). `n-51` is a node
whose active-locale (`en`) content is not yet authored — served as **non-selectable** with
`contentStatus: pending_locale` and `texto: null` (locale-fallback rule, §5, resolves gap G3). It is
never populated with the other locale's text.

**EARS**

- WHEN a valid `GET /tree` is received, THE SYSTEM SHALL return the full tree for the JWT's `pacienteId` scoped to its `empresa`.
- THE SYSTEM SHALL serve node structure (`parentId`, `tipo`, `ordem`, `engineFeatureRef`) identically regardless of the active locale.
- WHILE a node's active-locale content status is `pendente` or absent, THE SYSTEM SHALL return that node with `contentStatus: pending_locale`, `texto: null`, and `selecionavel: false`, and SHALL NOT substitute another locale's text.
- THE SYSTEM SHALL mark `categoria` nodes `selecionavel: false` and `frase` nodes `selecionavel` only when their active-locale content is `ativo`.

### 2.2 `POST /api/v1/conv/search` — assisted semantic search (kNN, retrieval-only)

Given a captured intent (free text), returns the closest **already-authored** nodes in the active
locale via Elasticsearch kNN. Never generative (`ADR-002`/ADR-015).

- **Scope:** `conv.arvore.navegar`
- **Request:**

```json
{ "intent": "some water please", "k": 5, "locale": "en" }
```

- **200 response (semantic path):**

```json
{
  "localeServed": "en",
  "degraded": false,
  "retrieval": "semantic",
  "matches": [
    { "noId": "n-42", "texto": "I want water with ice", "score": 0.91, "tipo": "frase",
      "engineFeatureRef": "gest:blink-double", "audioRef": { "present": true, "formato": "opus" } },
    { "noId": "n-88", "texto": "I am thirsty", "score": 0.83, "tipo": "frase",
      "audioRef": { "present": true, "formato": "opus" } }
  ]
}
```

- **200 response (degraded — ES unavailable, OPS-003):**

```json
{ "localeServed": "en", "degraded": true, "retrieval": "static",
  "matches": [], "reason": "semantic-retrieval-unavailable" }
```

The kNN query is built by the application layer and passed as the **full `_search` body** to
`SearchPort.search("neuroassist-ai_arvore-fala", body)` (see [`integration-contracts.md`](integration-contracts.md)
§1 and [`../design/index-design-arvore-fala.md`](../design/index-design-arvore-fala.md) for the resolved
kNN shape; the map carries a top-level `knn` block — **no SearchPort change required**).

**EARS**

- WHEN `POST /search` runs and Elasticsearch is reachable, THE SYSTEM SHALL return up to `k` nodes ordered by kNN score, filtered to the JWT's `pacienteId` and the active locale, and `retrieval: semantic`.
- THE SYSTEM SHALL only return nodes whose active-locale content is `ativo`; it SHALL NEVER return text that is not present in the curated tree.
- IF Elasticsearch is unreachable or the semantic (vector) half is unavailable, THEN THE SYSTEM SHALL return `200` with `degraded: true`, `retrieval: static`, and an empty `matches` array, and SHALL NOT fail the request (OPS-003).
- IF `k` is absent, THEN THE SYSTEM SHALL default it to `5`; IF `k > 50`, THEN THE SYSTEM SHALL clamp it to `50`.
- IF `intent` is blank or exceeds 512 characters, THEN THE SYSTEM SHALL reject with `400`.

### 2.3 `POST /api/v1/conv/nodes/{id}/select` — select (speak) a node

The anchor act. Returns the phrase text + the audio reference for the active locale, and emits
`SpeechNodeSelected` to HIST.

- **Scope:** `conv.no.selecionar`
- **Path param:** `{id}` — node id.
- **Request:**

```json
{ "via": "gesto", "locale": "en", "clientEventId": "9d1f…-uuid" }
```

`via` ∈ `gesto` | `toque`. `clientEventId` is a client-generated idempotency key (see SPEC-05).

- **200 response:**

```json
{
  "noId": "n-42",
  "localeServed": "en",
  "texto": "I want water with ice",
  "audioRef": { "present": true, "uri": "conv/p-1024/en/n-42.opus", "formato": "opus" },
  "playbackMode": "audio-cacheado",
  "degraded": false,
  "via": "gesto"
}
```

- **200 response (audio absent, OPS-004):**

```json
{ "noId": "n-42", "localeServed": "en", "texto": "I want water with ice",
  "audioRef": { "present": false }, "playbackMode": "web-speech", "degraded": true, "via": "gesto" }
```

**EARS**

- WHEN a `frase` node is selected with active-locale content `ativo`, THE SYSTEM SHALL return its `texto` and `audioRef` for the active locale and SHALL emit `SpeechNodeSelected` to HIST.
- IF the target node is a `categoria`, THEN THE SYSTEM SHALL reject with `409` (`type: …/node-not-selectable`) — only `frase` nodes are speakable.
- IF the active-locale content of the node is `pendente` or absent, THEN THE SYSTEM SHALL reject with `409` (`type: …/content-pending-locale`) and SHALL NOT speak another locale's text.
- WHEN the cached audio reference is absent or the audio store is unreachable, THE SYSTEM SHALL return the text with `audioRef.present: false`, `playbackMode: web-speech`, and `degraded: true` (OPS-004), and SHALL still emit `SpeechNodeSelected` recording the fallback.
- WHEN two selections carry the same `clientEventId` within the idempotency window, THE SYSTEM SHALL emit the audit fact only once (SPEC-05).

### 2.4 `POST /api/v1/conv/bindings/resolve` — resolve a gesture → node

Resolves a recognized gesture's `engineFeatureRef` to a bound tree node so the frontend can treat it
as a selection. The `engineFeatureRef` vocabulary is owned by PAC/GEST and resolved read-only via
`VocabularyPort` (`ADR-013`).

- **Scope:** `conv.arvore.navegar`
- **Request:**

```json
{ "engineFeatureRef": "gest:blink-double", "locale": "en" }
```

- **200 response (bound):**

```json
{ "engineFeatureRef": "gest:blink-double", "noId": "n-42", "bound": true, "selecionavel": true }
```

- **200 response (unbound):**

```json
{ "engineFeatureRef": "gest:blink-double", "noId": null, "bound": false }
```

**EARS**

- WHEN a known `engineFeatureRef` is bound to a node in the patient's tree, THE SYSTEM SHALL return that node id with `bound: true`.
- IF the `engineFeatureRef` is unknown to the shared vocabulary (PAC/GEST) or bound to no node, THEN THE SYSTEM SHALL return `200` with `bound: false` and `noId: null` (not an error).
- THE SYSTEM SHALL treat `engineFeatureRef` as an **opaque string identifier** and SHALL NOT parse its internal structure (gap G4 — format unconfirmed against the real vocabulary; see [`../06-risks-gaps-assumptions.md`](../06-risks-gaps-assumptions.md)).

### 2.5 `GET /api/v1/conv/health` — liveness/readiness projection

Non-secured lightweight health projection consumed by GWY/ops (Spring Boot Actuator `health` is the
canonical source; this is the CONV-domain readiness view). Reports the reachability of soft
dependencies so degradation is observable.

```json
{ "status": "UP", "elasticsearch": "REACHABLE", "audioStore": "REACHABLE",
  "acs": "REACHABLE", "degradedCapabilities": [] }
```

**EARS**

- WHILE Elasticsearch is unreachable, THE SYSTEM SHALL report `elasticsearch: UNREACHABLE` and include `semantic-search` in `degradedCapabilities`, while `status` stays `UP` (navigation still works).
- IF ACS is unreachable, THEN THE SYSTEM SHALL report `acs: UNREACHABLE` and `status: DEGRADED` — identity is a hard dependency (fail-closed).

---

## 3. Error model (RFC 7807 ProblemDetail)

Every error is a `ProblemDetail` (`application/problem+json`) carrying `type`, `title`, `status`,
`detail`, `instance`, and `correlationId`.

```json
{
  "type": "https://neuroassist.ai/problems/content-pending-locale",
  "title": "Node content not available in the active locale",
  "status": 409,
  "detail": "Node n-51 has no 'ativo' content for locale 'en'.",
  "instance": "/api/v1/conv/nodes/n-51/select",
  "correlationId": "c-8c1f…"
}
```

| `type` (suffix) | HTTP | When |
|---|---|---|
| `no-biometric-ingress` | 400 | Body/query carries image/frame/landmark data. |
| `invalid-request` | 400 | Blank/oversized `intent`, malformed body, unsupported `via`. |
| `unauthenticated` | 401 | Missing/invalid/expired JWT signature. |
| `session-revoked` | 401 | JWT is on the ACS blacklist (immediate revocation, `ADR-003`). |
| `forbidden-scope` | 403 | Token lacks the required scope, or carries caregiver/admin authority. |
| `unsupported-locale` | 400 | `Accept-Language`/`locale` not in `localesSuportados`. |
| `node-not-found` | 404 | Node id not in the patient's tree. |
| `node-not-selectable` | 409 | Target is a `categoria` (only `frase` is selectable). |
| `content-pending-locale` | 409 | Active-locale content is `pendente`/absent. |
| `retrieval-unavailable` | 200 (soft) | ES down — returned as `degraded`, NOT an error (search only). |

**EARS**

- THE SYSTEM SHALL return every error as an RFC 7807 `ProblemDetail` carrying `type`, `status`, `detail`, `instance`, and `correlationId`.
- IF the JWT is on the ACS blacklist, THEN THE SYSTEM SHALL reject the request with `401` (`session-revoked`) on **every** call, regardless of the token's non-expiry (fail-closed, `ADR-003`).
- IF the token carries any caregiver/admin authority, THEN THE SYSTEM SHALL reject with `403` even when a required CONV scope is also present.

---

## 4. Authentication & authorization

- **Model:** OAuth2 Resource Server (Spring Security), shared Keycloak realm. JWT validated by
  signature (`jwk-set-uri`) **and** against the ACS revocation blacklist on every request via
  `AcsSessionPort` (`ADR-003`).
- **Required claims:** `sub` (patient session id), `empresa` (tenant), `pacienteId`, `locale`,
  `scope` (must contain the endpoint's scope).
- **Scopes:** `conv.arvore.navegar` (§2.1/2.2/2.4/2.5), `conv.no.selecionar` (§2.3).
- **Service-to-service (CONV → PAC/GEST for `VocabularyPort`):** OAuth2 `client_credentials`
  (pattern of shared `ADR-007-edge-device-auth.md`), scope `pac.vocabulary.read` — see
  [`hub-integration.md`](hub-integration.md) §Vocabulary and gap G7.
- Full rules in [`../spec/backend/SPEC-06-security.md`](../spec/backend/SPEC-06-security.md).

> **Test config note (Learning D-492):** `application-test.yml` MUST set
> `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (a static test JWKS), **not** `issuer-uri`
> — `issuer-uri` triggers OIDC discovery at boot against a Keycloak that is not up in tests. Carried
> into scaffold/BDD.

---

## 5. Locale resolution & fallback contract (resolves gap G3)

The active locale is `Accept-Language` (if supported) else the JWT `locale` claim. For every node,
content is resolved **only** in the active locale:

| Active-locale content state | `GET /tree` node shape | `POST /select` outcome |
|---|---|---|
| `ativo` | `texto` filled, `selecionavel: true` | 200 with text + audioRef |
| `pendente` / not authored | `texto: null`, `contentStatus: pending_locale`, `selecionavel: false` | 409 `content-pending-locale` |
| locale not in `localesSuportados` | request rejected `400 unsupported-locale` | request rejected `400 unsupported-locale` |

**Decision (specs, closes ADR-018/ADR-005 open UX-adjacent question):** CONV **hides speakability**
of a node whose active-locale content is not `ativo` and flags it explicitly; it **never** substitutes
the other locale's text or audio. Cross-locale substitution is a safety violation in assistive health
(a patient in an EN session must not "say" a PT phrase they cannot read). The visual treatment of a
flagged node is a FE concern; the **contract** guarantees no silent wrong-locale content.

---

## 6. Non-functional invariants

- **Latency:** semantic search targets local kNN (no external round-trip) with low, predictable
  latency; navigation is a bounded `parentId` walk over a dozens-of-nodes tree.
- **Availability posture:** navigation and selection succeed while ES and/or the audio store are down
  (degrade, never stop); only identity/revocation is fail-closed.
- **No third-party API on the runtime path:** no LLM, no ElevenLabs, no embedding computed by CONV
  (the kNN query vector is built inside ES via `query_vector_builder`, see integration-contracts §1).
