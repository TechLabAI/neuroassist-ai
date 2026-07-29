# GEST — API Contract (`gest-service`)

> Module: **GEST — Detecção de Gesto (Edge)** · Service: `gest-service` · Package: `com.neuroassist.gest`
> Project: NeuroAssist AI · Multi-tenant: schema-per-empresa · Messaging: disabled (sync REST only)
> Source of truth for the edge `GestureDetectionEngine` client and for peer services (PAC, EVNT, EDGE, DASH).
> EARS notation (D-057) governs every endpoint invariant. Guide: `docs/architecture/backend/standards/ears-notation.md`.

`gest-service` is the **cloud-side detection authority** (ADR-005 BE). It never receives pixels; it owns
the TipoGesto vocabulary, the detectability lifecycle, the versioned detection profile, and the
**detection contract** (the frozen schema the edge uses to report a recognized command). This document
also freezes the localhost bridge schema consumed by the FE live-test (FE ADR-005) — closing the open
warning from the vision phase.

---

## 1. Conventions

- **Base path:** `/api/v1/gest` (routed through GWY; discovered via REG).
- **Media type:** `application/json` (requests and responses). Errors use `application/problem+json`.
- **Versioning:** URI-versioned (`/v1`). The detection contract carries its own semantic version
  (`X-Detection-Contract-Version`, see §7) independent of the URI version.
- **Identifiers:** all resource ids are `uuid` (string form, RFC 4122).
- **Timestamps:** ISO-8601 UTC (`2026-07-27T14:00:00Z`).
- **Pagination:** list endpoints accept `?page` (0-based) and `?size` (default 50, max 200); responses
  wrap items in `{ "items": [...], "page": n, "size": n, "total": n }`.

### 1.1 Mandatory headers

| Header | Direction | Purpose |
|---|---|---|
| `Authorization: Bearer <jwt>` | request | OAuth2 access token (see §6). |
| `X-Correlation-Id` | request/response | Trace correlation; echoed back. Generated at GWY if absent. |
| `X-Detection-Contract-Version` | response (all), request (report/stream) | Semantic version of the frozen detection contract in effect. |
| `X-Profile-Version` | response (profile endpoints) | Monotonic version of the returned detection profile. |

- WHEN a request omits `X-Correlation-Id`, THE SYSTEM SHALL generate one and return it in the response.
- THE SYSTEM SHALL derive `tenantId` exclusively from the JWT `tenant` claim and SHALL NOT accept a
  tenant id from the request body, query, or path.
- IF a request body carries a `tenantId` field that differs from the JWT `tenant` claim, THEN THE SYSTEM
  SHALL reject the request with `403 Forbidden` (ProblemDetail `type: /errors/tenant-mismatch`).

---

## 2. Resource: TipoGesto (primitive catalog)

The authoritative, tenant-scoped vocabulary of facial-movement primitives. See ADR-006 for the
detectability lifecycle.

### 2.1 `GET /api/v1/gest/tipos-gesto`

List the primitive catalog for the caller's tenant.

- Query: `?state=registered|pending_training|supported|deprecated` (repeatable), `?page`, `?size`.
- Response `200 OK`:

```json
{
  "items": [
    {
      "id": "3f1c…",
      "nome": "piscar duplo",
      "detectabilityState": "supported",
      "engineFeatureRef": "mp.blink.double.v1",
      "createdAt": "2026-07-27T12:00:00Z",
      "updatedAt": "2026-07-27T12:30:00Z"
    }
  ],
  "page": 0, "size": 50, "total": 1
}
```

- THE SYSTEM SHALL return only primitives belonging to the caller's tenant schema.
- WHEN no `state` filter is supplied, THE SYSTEM SHALL return primitives in all states including `deprecated`.

### 2.2 `GET /api/v1/gest/tipos-gesto/{id}`

- Response `200 OK` — single `TipoGesto` (shape as in §2.1 `items[]`).
- IF `{id}` does not exist in the caller's tenant, THEN THE SYSTEM SHALL respond `404 Not Found`
  (`type: /errors/tipo-gesto-not-found`).

### 2.3 `POST /api/v1/gest/tipos-gesto`

Register a new primitive. Entry state is always `registered` (ADR-006).

- Request:

```json
{ "nome": "leitura labial" }
```

- Response `201 Created` — `Location: /api/v1/gest/tipos-gesto/{id}`; body is the created `TipoGesto`
  with `detectabilityState: "registered"` and `engineFeatureRef: null`.
- THE SYSTEM SHALL create every new primitive in state `registered` regardless of request content.
- IF a primitive with the same `nome` (case-insensitive, trimmed) already exists in the tenant and is
  not `deprecated`, THEN THE SYSTEM SHALL respond `409 Conflict` (`type: /errors/tipo-gesto-duplicate`).
- IF `nome` is blank or longer than 120 chars, THEN THE SYSTEM SHALL respond `400 Bad Request`
  (`type: /errors/validation`).
- WHERE the caller lacks the conductor role, THE SYSTEM SHALL respond `403 Forbidden` (see §6.2).

### 2.4 `POST /api/v1/gest/tipos-gesto/{id}/transitions`

Drive the detectability state machine. This is the single mutation surface for lifecycle state
(no direct `PATCH` of `detectabilityState`).

- Request:

```json
{
  "toState": "pending_training | supported | deprecated",
  "engineFeatureRef": "mp.blink.double.v1",
  "reason": "engine capability validated on device D-12 (out-of-band)"
}
```

- Response `200 OK` — updated `TipoGesto` + the appended transition record.
- Allowed transitions (ADR-006 / Framing §"State Transitions"):
  `registered → pending_training`, `registered → supported` (seed only), `pending_training → supported`,
  `pending_training → deprecated`, `supported → deprecated`.
- IF `toState` is not reachable from the current state per the allowed transition set, THEN THE SYSTEM
  SHALL respond `409 Conflict` (`type: /errors/illegal-transition`) and SHALL NOT mutate state.
- IF `toState = supported` AND `engineFeatureRef` (resolved from request or existing value) is null,
  THEN THE SYSTEM SHALL reject the transition with `422 Unprocessable Entity`
  (`type: /errors/engine-feature-ref-required`) — invariant #5 (ADR-006).
- WHEN any transition succeeds, THE SYSTEM SHALL append an immutable record to `tipo_gesto_transition`
  capturing `fromState`, `toState`, `actor`, `reason`, `at`.
- IF a transition would regress a `supported` primitive to a lower state without an explicit
  `reason`, THEN THE SYSTEM SHALL respond `400 Bad Request` (`type: /errors/regression-reason-required`)
  — invariant #4 (no silent regression).
- WHERE the caller lacks the conductor role, THE SYSTEM SHALL respond `403 Forbidden`.

---

## 3. Resource: Eligibility (emergency-command guard)

The enforcement point for the safety invariant. Called synchronously by **PAC** while building a
`Comando` (messaging disabled → REST).

### 3.1 `POST /api/v1/gest/eligibility:check`

- Request:

```json
{
  "urgencia": "emergencia | rotina",
  "primitiveIds": ["3f1c…", "9a20…"]
}
```

- Response `200 OK`:

```json
{
  "eligible": false,
  "urgencia": "emergencia",
  "results": [
    { "primitiveId": "3f1c…", "detectabilityState": "supported", "eligible": true },
    { "primitiveId": "9a20…", "detectabilityState": "registered", "eligible": false,
      "reason": "primitive is not 'supported'" }
  ]
}
```

- WHEN `urgencia = emergencia`, THE SYSTEM SHALL return `eligible: true` only if every primitive in
  `primitiveIds` is in state `supported` — invariant #1 (ADR-006); for composite gestures both
  primitives SHALL be `supported`.
- WHEN `urgencia = rotina`, THE SYSTEM SHALL return `eligible: true` if every primitive exists and is
  not `deprecated`.
- IF any `primitiveId` does not exist in the caller's tenant, THEN THE SYSTEM SHALL respond
  `422 Unprocessable Entity` (`type: /errors/unknown-primitive`) listing the offending ids.
- THE SYSTEM SHALL treat this endpoint as read-only (no state mutation, idempotent).

---

## 4. Resource: Detection Profile

A versioned, immutable bundle of `supported` primitives + classification parameters, pulled by the
edge. See §"Async vs Sync Decisions" (doc 04) — pull, not push.

### 4.1 `POST /api/v1/gest/detection-profiles`

Build and publish a new profile version from the tenant's current `supported` primitives.

- Request (optional overrides; defaults come from the tenant's calibration state):

```json
{
  "classificationParams": {
    "confidenceThreshold": 0.82,
    "minWindowMs": 300,
    "maxWindowMs": 2500,
    "compositeMaxGapMs": 800
  }
}
```

- Response `201 Created` — `Location: /api/v1/gest/detection-profiles/{version}`; body is the published
  profile (see §4.3 shape) with a freshly allocated monotonic `version` and `active: true`.
- WHEN a profile is published, THE SYSTEM SHALL include only primitives in state `supported` at publish
  time — invariant #2 (ADR-006).
- THE SYSTEM SHALL assign a strictly monotonic, never-reused `version` per tenant — invariant #3.
- WHEN a new profile becomes `active`, THE SYSTEM SHALL set every prior profile of the tenant to
  `active: false` without mutating their content (immutability preserved).
- IF the tenant has zero `supported` primitives, THEN THE SYSTEM SHALL respond `422 Unprocessable
  Entity` (`type: /errors/empty-profile`) and SHALL NOT create a version.
- WHERE the caller lacks the conductor role, THE SYSTEM SHALL respond `403 Forbidden`.

### 4.2 `GET /api/v1/gest/detection-profiles/active`

- Response `200 OK` — the currently active profile (shape §4.3). `X-Profile-Version` header set.
- IF no active profile exists for the tenant, THEN THE SYSTEM SHALL respond `404 Not Found`
  (`type: /errors/no-active-profile`).

### 4.3 `GET /api/v1/gest/detection-profiles/{version}`

- Response `200 OK`:

```json
{
  "id": "b7…",
  "version": 7,
  "active": true,
  "contractVersion": "1.0.0",
  "classificationParams": {
    "confidenceThreshold": 0.82, "minWindowMs": 300, "maxWindowMs": 2500, "compositeMaxGapMs": 800
  },
  "primitives": [
    { "tipoGestoId": "3f1c…", "nome": "piscar duplo", "engineFeatureRef": "mp.blink.double.v1" }
  ],
  "publishedAt": "2026-07-27T13:00:00Z"
}
```

- THE SYSTEM SHALL return the exact immutable content published at that version.
- IF `{version}` was never published for the tenant, THEN THE SYSTEM SHALL respond `404 Not Found`.

### 4.4 `GET /api/v1/gest/devices/{deviceId}/detection-profile`  — edge pull

The endpoint the edge `GestureDetectionEngine` calls to configure itself. Authenticated with a
**device credential** (§6.3), not a human token.

- Response `200 OK` — the active profile for the device's tenant (shape §4.3) + `X-Profile-Version`.
- Supports conditional GET: WHEN the request carries `If-None-Match` equal to the active profile's
  ETag (version), THE SYSTEM SHALL respond `304 Not Modified` with no body (bandwidth-friendly polling
  aligned with EDGE heartbeat).
- IF `{deviceId}` is not paired to the caller's tenant (resolved via EDGE), THEN THE SYSTEM SHALL
  respond `403 Forbidden` (`type: /errors/device-not-paired`).
- THE SYSTEM SHALL function regardless of edge liveness — this endpoint is pull-based and stateless
  with respect to device connectivity (principle #5, doc 01).

---

## 5. Detection contract endpoints (edge ↔ cloud, frozen schema)

GEST **owns** the shape of a recognized-command report and the live-test signal. These are the
schemas the vision-phase warning required to be frozen. See `event-contracts.md` §3 for the full
payload definition, idempotency and versioning; here we fix the transport.

### 5.1 `GET /api/v1/gest/detection-contract`

Returns the machine-readable descriptor (JSON Schema + version) of the current detection contract, so
edge and EVNT agree without a redeploy.

- Response `200 OK`:

```json
{
  "contractVersion": "1.0.0",
  "recognizedCommandReportSchema": { "$ref": "…/RecognizedCommandReport.schema.json" },
  "edgeRecognitionSignalSchema": { "$ref": "…/EdgeRecognitionSignal.schema.json" }
}
```

- THE SYSTEM SHALL expose the current contract version at this endpoint and SHALL keep it in sync with
  the `X-Detection-Contract-Version` header on every response.

### 5.2 `POST /api/v1/gest/recognized-commands`  — validate-only ingress (optional guard)

Production telemetry is delivered by the edge to **EVNT** (which builds `Evento`). GEST additionally
exposes a **stateless validation** endpoint so the edge/EVNT can assert a report conforms to the
frozen contract for the active profile.

- Request: a `RecognizedCommandReport` (see `event-contracts.md` §3.1).
- Response `200 OK`: `{ "valid": true, "contractVersion": "1.0.0" }` or `422` with violations.
- IF the report's `profileVersion` is not a published version for the tenant, THEN THE SYSTEM SHALL
  respond `422 Unprocessable Entity` (`type: /errors/unknown-profile-version`).
- IF a reported primitive is not a member of the referenced profile version, THEN THE SYSTEM SHALL
  respond `422 Unprocessable Entity` (`type: /errors/primitive-not-in-profile`).
- THE SYSTEM SHALL NOT persist any report and SHALL NOT store confidence/timing telemetry — validation
  is stateless and no biometric-adjacent data is retained (privacy invariant, doc 02 / doc 05 §LGPD).

### 5.3 Live-test bridge schema (FE ADR-005) — closing the vision warning

The FE live-test bridges to the **local** edge agent over a localhost channel (WS/SSE); that transport
is FE↔edge and is **not** served by gest-service. GEST's responsibility is to **freeze the message
schema** `EdgeRecognitionSignal` (defined in `event-contracts.md` §3.2) and publish it via §5.1, so the
browser and the edge agent share one authoritative shape. The bridge handshake/auth (localhost token,
`commandDraft` framing) is owned jointly by the FE and edge/BE teams; this contract fixes the payload,
the `contractVersion` negotiation, and the degraded-mode marker (`degraded: true`, no fake "recognized").

---

## 6. Authentication & Authorization

### 6.1 OAuth2

- THE SYSTEM SHALL require a valid OAuth2 Bearer JWT on every endpoint except health/observability
  probes (see SPEC-07 when generated).
- THE SYSTEM SHALL validate the token signature against the configured `jwk-set-uri` and reject
  expired or malformed tokens with `401 Unauthorized`.

### 6.2 Scopes / roles (from ACS)

| Operation | Required role/scope |
|---|---|
| Read catalog / profiles / eligibility (§2.1, §2.2, §3, §4.2, §4.3, §5.1) | `gest.read` (any authenticated tenant member) |
| Register primitive, transition lifecycle, publish profile (§2.3, §2.4, §4.1) | `gest.manage` (conductor role) |
| Edge profile pull, report validation (§4.4, §5.2) | device credential (§6.3) |

- WHERE the caller's token lacks the required scope, THE SYSTEM SHALL respond `403 Forbidden`
  (`type: /errors/insufficient-scope`).

### 6.3 Edge device credential

- THE SYSTEM SHALL authenticate edge profile pulls with a device credential whose `deviceId` and
  `tenant` claims are resolved through EDGE pairing.
- IF the device credential's tenant does not match the target profile's tenant, THEN THE SYSTEM SHALL
  respond `403 Forbidden`.
- **Gap CLOSED (D-534, 2026-07-29):** The device-credential format is **OAuth2 `client_credentials`
  issued by ACS**. Each enrolled device receives a `clientId`/`clientSecret` pair (via `POST /api/v1/edge/devices/enroll`);
  the PEA exchanges these for a short-lived JWT (TTL 900s, role `edge_agent`, scope `edge:publish`)
  via `POST /oauth2/token`. GEST validates this JWT against the ACS JWK set (`jwk-set-uri`). mTLS is
  out of scope for MVP. The `deviceId` is embedded in the JWT `sub` claim.

---

## 7. Errors — RFC 7807 ProblemDetail

All error responses use `application/problem+json`:

```json
{
  "type": "/errors/illegal-transition",
  "title": "Illegal detectability transition",
  "status": 409,
  "detail": "Cannot transition 'registered' → 'deprecated' for TipoGesto 3f1c…",
  "instance": "/api/v1/gest/tipos-gesto/3f1c…/transitions",
  "correlationId": "…",
  "errors": []
}
```

| `type` | HTTP | When |
|---|---|---|
| `/errors/validation` | 400 | Malformed/blank field. |
| `/errors/regression-reason-required` | 400 | Regression without `reason`. |
| `/errors/tenant-mismatch` | 403 | Body tenant ≠ JWT tenant. |
| `/errors/insufficient-scope` | 403 | Missing role/scope. |
| `/errors/device-not-paired` | 403 | Device/tenant mismatch on edge pull. |
| `/errors/tipo-gesto-not-found` | 404 | Unknown primitive id. |
| `/errors/no-active-profile` | 404 | No active profile for tenant. |
| `/errors/tipo-gesto-duplicate` | 409 | Duplicate `nome` in tenant. |
| `/errors/illegal-transition` | 409 | Transition not in allowed set. |
| `/errors/engine-feature-ref-required` | 422 | `supported` without `engineFeatureRef`. |
| `/errors/empty-profile` | 422 | Publish with zero supported primitives. |
| `/errors/unknown-primitive` | 422 | Eligibility check on unknown id. |
| `/errors/unknown-profile-version` | 422 | Report references unpublished version. |
| `/errors/primitive-not-in-profile` | 422 | Reported primitive not in profile. |

- THE SYSTEM SHALL include the `correlationId` (from `X-Correlation-Id`) in every ProblemDetail body.
- THE SYSTEM SHALL NOT include any biometric, video, image, or landmark data in an error body or log.

---

## 8. Endpoint summary

| Verb | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/v1/gest/tipos-gesto` | `gest.read` | List primitive catalog |
| GET | `/api/v1/gest/tipos-gesto/{id}` | `gest.read` | Get primitive |
| POST | `/api/v1/gest/tipos-gesto` | `gest.manage` | Register primitive (`registered`) |
| POST | `/api/v1/gest/tipos-gesto/{id}/transitions` | `gest.manage` | Lifecycle transition |
| POST | `/api/v1/gest/eligibility:check` | `gest.read` | Emergency-command guard (PAC) |
| POST | `/api/v1/gest/detection-profiles` | `gest.manage` | Build & publish profile version |
| GET | `/api/v1/gest/detection-profiles/active` | `gest.read` | Active profile |
| GET | `/api/v1/gest/detection-profiles/{version}` | `gest.read` | Profile by version |
| GET | `/api/v1/gest/devices/{deviceId}/detection-profile` | device cred | Edge pull (ETag/304) |
| GET | `/api/v1/gest/detection-contract` | `gest.read` | Frozen contract descriptor |
| POST | `/api/v1/gest/recognized-commands` | device cred | Validate report (stateless) |
