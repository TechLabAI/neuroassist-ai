# EDGE — API Contract (`edge-service`)

> Module: **EDGE — Integração de Dispositivo Edge** · Service: `edge-service` · Package: `com.neuroassist.edge`
> Project: NeuroAssist AI · Multi-tenant: schema-per-empresa · Messaging: disabled (sync REST only)
> Source of truth for the **edge agent** client and for peer services (PAC, GEST, DASH).
> EARS notation (D-057) governs every endpoint invariant. Guide: `docs/architecture/backend/standards/ears-notation.md`.

`edge-service` is the backend **device authority** (ADR-005 / ADR-006 BE). It owns the identity of each
edge agent, its exclusive pairing to a patient, and the heartbeat-derived liveness signal every other
module trusts to mean "monitoring is really running". It never receives a pixel, image, or landmark.
This document **freezes the two open decisions the vision phase deferred to Specs** (doc 06 G1/G2/G3/G4):
the device-credential mechanism (§6) and the numeric staleness/heartbeat parameters (§5). It also honors
the inbound endpoint GEST already froze against EDGE (`GET /devices/{deviceId}/tenant`, GEST
`integration-contracts.md` §1).

---

## 1. Conventions

- **Base path:** `/api/v1/edge` (routed through GWY; discovered via REG). The edge agent never reaches
  `edge-service` directly — only via GWY.
- **Media type:** `application/json` (requests and responses). Errors use `application/problem+json`.
- **Versioning:** URI-versioned (`/v1`).
- **Identifiers:** all resource ids are `uuid` (string form, RFC 4122). `deviceId` is the
  `DispositivoEdge` id; `pacienteId` is a cross-service reference into PAC (never a DB FK).
- **Timestamps:** ISO-8601 UTC (`2026-07-29T14:00:00Z`). Server clock is authoritative for liveness (§5).
- **Pagination:** list endpoints accept `?page` (0-based) and `?size` (default 50, max 200); responses
  wrap items in `{ "items": [...], "page": n, "size": n, "total": n }`.

### 1.1 Mandatory headers

| Header | Direction | Purpose |
|---|---|---|
| `Authorization: Bearer <jwt>` | request | OAuth2 access token — human, service, or **device** token (see §6). |
| `X-Correlation-Id` | request/response | Trace correlation; echoed back. Generated at GWY if absent. |
| `X-Staleness-Threshold-Seconds` | response (liveness/heartbeat) | The staleness window in effect (§5), so readers interpret `status` consistently. |
| `ETag` / `If-None-Match` | response / request (liveness poll) | Bandwidth-friendly DASH polling (§4.4). |

- WHEN a request omits `X-Correlation-Id`, THE SYSTEM SHALL generate one and return it in the response.
- THE SYSTEM SHALL derive `tenantId` exclusively from the caller's token claims (JWT `tenant`, device
  token `tenant`, or service token `tenant`) and SHALL NOT accept a tenant id from the request body,
  query, or path.
- THE SYSTEM SHALL derive the acting `deviceId` on the heartbeat and enroll paths **exclusively from the
  authenticated device credential**, never from the request body — anti-spoofing invariant (ADR-005,
  Framing invariant #2).
- IF a request body carries a `tenantId` or `deviceId` that differs from the authenticated principal,
  THEN THE SYSTEM SHALL reject the request with `403 Forbidden` (`type: /errors/identity-mismatch`).

---

## 2. Resource: Device registration & enrollment (bootstrap trust — doc 06 G4)

Registration is **operator-driven** (Danilo, `edge.manage`); the edge agent then **enrolls** to obtain
its own credential. Two steps so no shared secret exists and the device proves itself thereafter.

### 2.1 `POST /api/v1/edge/devices` — register a device (operator)

Creates a `DispositivoEdge` in state `registered` and mints a **one-time, short-lived enrollment token**
(claim code) the operator hands to the agent during onboarding.

- Auth: `edge.manage` (conductor role).
- Request:

```json
{ "label": "Câmera quarto do Márcio", "pacienteHint": "9c2a…" }
```

- Response `201 Created` — `Location: /api/v1/edge/devices/{id}`:

```json
{
  "deviceId": "d7b1…",
  "pairingState": "registered",
  "label": "Câmera quarto do Márcio",
  "enrollmentToken": "et_9f3c…",
  "enrollmentExpiresAt": "2026-07-29T14:15:00Z",
  "createdAt": "2026-07-29T14:00:00Z"
}
```

- THE SYSTEM SHALL create every new device in state `registered` regardless of request content.
- THE SYSTEM SHALL return the `enrollmentToken` **exactly once**, at registration, and SHALL store only
  its hash (`dispositivo_enrollment.token_hash`).
- THE SYSTEM SHALL set `enrollmentExpiresAt` to `now + enrollmentTtl` (default **15 minutes**, §5) and
  SHALL mark the token single-use.
- `pacienteHint` is advisory only (pre-fills the pairing UI); THE SYSTEM SHALL NOT pair on registration.
- WHERE the caller lacks the `edge.manage` scope, THE SYSTEM SHALL respond `403 Forbidden`
  (`type: /errors/insufficient-scope`).

### 2.2 `POST /api/v1/edge/devices/enroll` — complete enrollment (edge agent)

The agent exchanges the one-time enrollment token for its **device credential** (OAuth2
`client_credentials`, §6.3). This is the only unauthenticated business endpoint — it is guarded by the
single-use token, not a Bearer JWT.

- Auth: none (bearer token not required); the enrollment token is the proof.
- Request:

```json
{ "enrollmentToken": "et_9f3c…", "agentVersion": "1.4.2" }
```

- Response `200 OK` — the device credential, returned **once**:

```json
{
  "deviceId": "d7b1…",
  "tenantRef": "empresa-0001",
  "clientId": "edge-d7b1…",
  "clientSecret": "cs_2a91…",
  "tokenEndpoint": "https://acs/oauth2/token",
  "tokenScope": "edge.device",
  "recommendedHeartbeatSeconds": 30
}
```

- WHEN a valid, unexpired, unconsumed enrollment token is presented, THE SYSTEM SHALL issue the device
  credential, persist only its hash (`dispositivo_credential.secret_hash`), mark the enrollment token
  consumed, and return `clientSecret` exactly once.
- IF the enrollment token is unknown, expired, or already consumed, THEN THE SYSTEM SHALL respond
  `401 Unauthorized` (`type: /errors/enrollment-invalid`) and SHALL NOT reveal which condition failed.
- IF the device is already `paired`/`unpaired`/`retired` (i.e. already enrolled), THEN THE SYSTEM SHALL
  respond `409 Conflict` (`type: /errors/already-enrolled`).
- THE SYSTEM SHALL record `agentVersion` as the initial `versao` (not yet a heartbeat — see §3).

### 2.3 `POST /api/v1/edge/devices/{id}:retire` — retire a device (operator)

- Auth: `edge.manage`. Response `200 OK` — device with `pairingState: "retired"`.
- WHEN a device is retired, THE SYSTEM SHALL revoke its credential, drop any active pairing (audited),
  and treat the device as terminal — Framing state machine (`retired` is terminal).
- THE SYSTEM SHALL never hard-delete a device row so historical `Evento` references keep resolving
  (doc 05 Data Lifecycle).

---

## 3. Resource: Heartbeat & liveness (anchor — ADR-006)

### 3.1 `POST /api/v1/edge/heartbeat` — record a heartbeat (edge agent)

The recurring core. The reporting device is the authenticated device; the body carries **only** liveness
+ version metadata. The response piggybacks the current detection-profile **version pointer** (ADR-006).

- Auth: **device credential** (§6.3). `deviceId` comes from the token, never the body.
- Request:

```json
{ "agentVersion": "1.4.2", "runningProfileVersion": 6 }
```

- Response `200 OK`:

```json
{
  "deviceId": "d7b1…",
  "status": "online",
  "serverTime": "2026-07-29T14:00:30Z",
  "ultimaSincronizacao": "2026-07-29T14:00:30Z",
  "currentProfileVersion": 7,
  "profileDrift": true,
  "stalenessThresholdSeconds": 90,
  "nextHeartbeatSeconds": 30
}
```

- WHEN a heartbeat is accepted, THE SYSTEM SHALL stamp `ultimaSincronizacao` with the **server clock**
  (not the client timestamp) — mitigates edge clock skew (doc 06 R5).
- WHEN a heartbeat is accepted, THE SYSTEM SHALL record `versao = agentVersion` and
  `runningProfileVersion` on the aggregate (latest value only; doc 05).
- WHEN the device was `offline` at read time and a heartbeat is accepted, THE SYSTEM SHALL append a
  `liveness` transition (`offline → online`) to `dispositivo_edge_transition` — no silent change
  (Framing invariant #3 / doc 05).
- THE SYSTEM SHALL fetch `currentProfileVersion` from GEST (`DetectionProfilePort`, §GEST
  `detection-profiles/active`) and SHALL set `profileDrift = (runningProfileVersion != currentProfileVersion)`.
- IF GEST is unavailable, THEN THE SYSTEM SHALL still accept the heartbeat, SHALL omit
  `currentProfileVersion`/`profileDrift`, and SHALL set `profilePointerDegraded: true` — liveness never
  depends on GEST (doc 06, ADR-006).
- IF the body carries a `deviceId` that differs from the token identity, THEN THE SYSTEM SHALL respond
  `403 Forbidden` (`type: /errors/identity-mismatch`).
- IF the authenticated device is `retired`, THEN THE SYSTEM SHALL respond `409 Conflict`
  (`type: /errors/device-retired`).
- THE SYSTEM SHALL NOT accept any video, image, landmark, or biometric field in the heartbeat body
  (privacy invariant, doc 02 / Framing invariant #6).

### 3.2 Liveness derivation (read model)

`status` is **derived, never stored** (ADR-006, doc 05):

- THE SYSTEM SHALL compute `status = online` WHILE `serverNow − ultimaSincronizacao ≤ stalenessThreshold`,
  and `offline` otherwise.
- WHILE a paired device has never sent a heartbeat, THE SYSTEM SHALL report `status = offline`.
- WHERE a device is `unpaired` or `retired`, THE SYSTEM SHALL report `status = "n/a"` (liveness has no
  meaning without an active pairing — Framing "Liveness").

---

## 4. Resource: Device read & liveness query (internal consumers)

### 4.1 `GET /api/v1/edge/devices/{deviceId}` — full device (PAC / DASH / operator)

- Auth: `edge.read` (tenant member) or service credential (§6.4).
- Response `200 OK`:

```json
{
  "deviceId": "d7b1…",
  "tenantRef": "empresa-0001",
  "pacienteId": "9c2a…",
  "pairingState": "paired",
  "status": "online",
  "ultimaSincronizacao": "2026-07-29T14:00:30Z",
  "versao": "1.4.2",
  "runningProfileVersion": 6,
  "currentProfileVersion": 7,
  "profileDrift": true,
  "stalenessThresholdSeconds": 90
}
```

- THE SYSTEM SHALL compute `status`/`profileDrift` at read time (§3.2); they are not stored columns.
- IF `{deviceId}` does not exist in the caller's tenant, THEN THE SYSTEM SHALL respond `404 Not Found`
  (`type: /errors/device-not-found`).

### 4.2 `GET /api/v1/edge/devices/{deviceId}/tenant` — device→tenant resolution (GEST) — FROZEN

The endpoint **GEST already froze** against EDGE (GEST `integration-contracts.md` §1) so GEST can verify a
`deviceId` belongs to the calling tenant before serving a profile pull.

- Auth: service credential (§6.4) — GEST's own `client_credentials`.
- Response `200 OK`: `{ "deviceId": "d7b1…", "tenantRef": "empresa-0001" }`.
- IF the device is not registered (or is `retired`), THEN THE SYSTEM SHALL respond `404 Not Found`
  (`type: /errors/device-not-found`) — GEST maps this to its `403 /errors/device-not-paired`.
- THE SYSTEM SHALL return only `deviceId` + `tenantRef` — no patient, liveness, or credential data.

### 4.3 `GET /api/v1/edge/patients/{pacienteId}/device` — resolve device for patient (PAC `EdgeDevicePort`)

PAC validates `paciente.dispositivo_edge_id` at write time against EDGE (cross-service reference, not FK —
ADR-005, doc 06 Critical Dependencies).

- Auth: `edge.read` or service credential.
- Response `200 OK`: the paired device (shape §4.1) whose `pairingState = paired`.
- IF no device is currently `paired` to `{pacienteId}` in the tenant, THEN THE SYSTEM SHALL respond
  `404 Not Found` (`type: /errors/patient-not-paired`).
- THE SYSTEM SHALL guarantee **referential honesty** (Framing invariant #5): the returned device is
  registered and currently paired to that same patient, or the call is `404`.

### 4.4 `GET /api/v1/edge/devices/{deviceId}/liveness` — liveness poll (DASH)

DASH has **no push** in the MVP (messaging disabled) and learns of a drop by polling this endpoint
(ADR-006, doc 06 R1). Lightweight and cache-friendly.

- Auth: `edge.read`.
- Response `200 OK`:

```json
{
  "deviceId": "d7b1…",
  "pacienteId": "9c2a…",
  "status": "offline",
  "ultimaSincronizacao": "2026-07-29T13:57:10Z",
  "versao": "1.4.2",
  "stalenessThresholdSeconds": 90,
  "serverTime": "2026-07-29T14:00:30Z"
}
```

- THE SYSTEM SHALL derive `status` at read time (§3.2) so a silently-dead device can never read `online`
  once the window elapsed (doc 06 R2).
- WHEN the caller sends `If-None-Match` equal to the current liveness ETag AND the derived `status` is
  unchanged, THE SYSTEM SHALL respond `304 Not Modified` with no body. The ETag SHALL incorporate the
  derived `status` **bucket boundary**, not just `ultimaSincronizacao`, so an `online→offline` crossing
  always invalidates the ETag even without a new heartbeat.
- `GET /api/v1/edge/patients/{pacienteId}/liveness` is the by-patient variant with the same semantics.

---

## 5. Operational parameters (closing doc 06 G2/G3/G4)

Fixed here as **defaults**, overridable per deployment via config (SPEC-07). The staleness rule is
`stalenessThreshold = heartbeatFactor × heartbeatInterval`.

| Parameter | Config key | Default | Rationale |
|---|---|---|---|
| Heartbeat interval | `neuroassist.edge.heartbeat.interval-seconds` | `30` | Balances liveness freshness vs. edge cost/energy (doc 06 G3). |
| Staleness factor | `neuroassist.edge.heartbeat.staleness-factor` | `3` | Tolerates 2 missed beats before `offline` — avoids flapping. |
| Staleness threshold | (derived) | `90` s | `3 × 30 s`; the max age of `ultimaSincronizacao` before `offline` (doc 06 G2). |
| DASH poll interval | `neuroassist.edge.liveness.poll-hint-seconds` | `15` | Advisory to DASH; bounds detection latency. |
| Enrollment token TTL | `neuroassist.edge.enrollment.ttl-seconds` | `900` | 15-min single-use claim window (doc 06 G4). |

- Worst-case caregiver-visible detection latency ≈ `stalenessThreshold + pollInterval` ≈ **105 s** in the
  MVP — the accepted trade-off (doc 06 R1), to be revisited with a broker when patient volume grows.
- THE SYSTEM SHALL expose the effective `stalenessThresholdSeconds` in every liveness/heartbeat response
  so every reader interprets `status` with the same window.

---

## 6. Authentication & Authorization

### 6.1 OAuth2 (human + service)

- THE SYSTEM SHALL require a valid OAuth2 Bearer JWT on every endpoint except `/actuator/health`,
  `/actuator/info`, and `POST /devices/enroll` (guarded by the single-use enrollment token, §2.2).
- THE SYSTEM SHALL validate the token signature against the configured `jwk-set-uri` (ACS) and reject
  expired or malformed tokens with `401 Unauthorized`.

### 6.2 Scopes / roles

| Operation | Required role/scope |
|---|---|
| Register device, retire, pair, unpair (§2.1, §2.3, §7) | `edge.manage` (conductor role, from ACS) |
| Read device / liveness (§4.1, §4.3, §4.4) | `edge.read` (any authenticated tenant member) |
| Heartbeat (§3.1) | device credential (`edge.device` scope) |
| Enroll (§2.2) | one-time enrollment token (no Bearer) |
| Device→tenant, resolve-device internal reads (§4.2, §4.3) | service credential (`edge.internal` scope) or `edge.read` |

- WHERE the caller's token lacks the required scope, THE SYSTEM SHALL respond `403 Forbidden`
  (`type: /errors/insufficient-scope`).

### 6.3 Device credential mechanism — DECISION (closes doc 06 G1)

The vision phase deferred the concrete mechanism (mTLS vs. device token vs. `client_credentials`). **This
contract freezes it:**

- THE SYSTEM SHALL credential each device as an **OAuth2 `client_credentials` client** (one `clientId` +
  hashed `clientSecret` per `DispositivoEdge`), issued at enrollment (§2.2) and rotated on re-pairing (§7).
- THE SYSTEM SHALL require the device to obtain a short-lived **device access token** (JWT) from ACS's
  token endpoint using its `client_credentials`, and to present it as `Authorization: Bearer` on
  heartbeat calls. The device token carries `deviceId` and `tenant` claims.
- THE SYSTEM SHALL resolve the acting `deviceId` and `tenant` from these claims only, never from the body.
- **Alignment (no rework):** this matches the **JWT-shaped device token carrying `deviceId` + `tenant`**
  that GEST already assumed provisionally (GEST `api-contract.md` §6.3, `SPEC-06` §3.2). The GEST open
  item is hereby closed with `client_credentials`; **mTLS is recorded as deferred hardening** (see
  SPEC-06 §"Future hardening"), not required for the MVP.
- WHEN a device is retired or re-paired, THE SYSTEM SHALL revoke the superseded credential so a stale
  secret can never authenticate (doc 05 Data Lifecycle).

### 6.4 Service credential (inter-module)

- THE SYSTEM SHALL authenticate inter-module callers (GEST `§4.2`, PAC `§4.3`) with an OAuth2
  `client_credentials` **service** token carrying `tenant` context, scope `edge.internal`.
- IF a service token's `tenant` does not match the target device's tenant, THEN THE SYSTEM SHALL respond
  as if the device does not exist (`404`) to avoid cross-tenant enumeration (SPEC-06).

---

## 7. Resource: Pairing (exclusive — ADR-005)

### 7.1 `POST /api/v1/edge/devices/{id}/pairings` — pair to a patient (operator)

- Auth: `edge.manage`. Request: `{ "pacienteId": "9c2a…" }`.
- Response `201 Created` — device with `pairingState: "paired"` and the appended pairing transition.
- WHEN pairing succeeds, THE SYSTEM SHALL move the device `registered|unpaired → paired`, set
  `pacienteId`, and append a `pairing` transition (`actor`, `reason`) — audited (ADR-005, invariant #3).
- IF the patient already has a device in `paired` state, THEN THE SYSTEM SHALL respond `409 Conflict`
  (`type: /errors/pairing-conflict`) — exclusive pairing, one active device per patient (invariant #1).
- IF the device is already `paired` (to any patient), THEN THE SYSTEM SHALL respond `409 Conflict`
  (`type: /errors/pairing-conflict`).
- IF the device is `retired`, THEN THE SYSTEM SHALL respond `409 Conflict` (`type: /errors/device-retired`).
- THE SYSTEM SHALL NOT validate `pacienteId` existence against PAC at pairing time (PAC owns that write
  path and validates the device against EDGE, not the reverse) — cross-service ref, not FK.

### 7.2 `DELETE /api/v1/edge/devices/{id}/pairings/current` — unpair (operator)

- Auth: `edge.manage`. Query/body: `{ "reason": "device replacement" }`.
- Response `204 No Content`.
- WHEN unpairing succeeds, THE SYSTEM SHALL move `paired → unpaired`, clear `pacienteId`, and append an
  audited `pairing` transition. Device replacement is unpair-old (then `:retire`) + pair-new (§7.1).
- IF the device is not currently `paired`, THEN THE SYSTEM SHALL respond `409 Conflict`
  (`type: /errors/illegal-pairing-transition`).

---

## 8. Errors — RFC 7807 ProblemDetail

All error responses use `application/problem+json`:

```json
{
  "type": "/errors/pairing-conflict",
  "title": "Exclusive pairing violated",
  "status": 409,
  "detail": "Patient 9c2a… already has an active paired device.",
  "instance": "/api/v1/edge/devices/d7b1…/pairings",
  "correlationId": "…",
  "errors": []
}
```

| `type` | HTTP | When |
|---|---|---|
| `/errors/validation` | 400 | Malformed/blank field. |
| `/errors/enrollment-invalid` | 401 | Enrollment token unknown/expired/consumed. |
| `/errors/identity-mismatch` | 403 | Body `deviceId`/`tenantId` ≠ authenticated principal. |
| `/errors/insufficient-scope` | 403 | Missing role/scope. |
| `/errors/device-not-found` | 404 | Unknown device in caller's tenant. |
| `/errors/patient-not-paired` | 404 | No paired device for patient. |
| `/errors/already-enrolled` | 409 | Enroll attempted on an already-enrolled device. |
| `/errors/pairing-conflict` | 409 | Exclusive-pairing invariant violated. |
| `/errors/illegal-pairing-transition` | 409 | Transition not in the allowed pairing set. |
| `/errors/device-retired` | 409 | Operation on a terminal (retired) device. |
| `/errors/optimistic-lock` | 409 | Concurrent mutation (row_version mismatch — SPEC-05). |

- THE SYSTEM SHALL include the `correlationId` (from `X-Correlation-Id`) in every ProblemDetail body.
- THE SYSTEM SHALL NOT include any biometric, video, image, landmark, or credential secret in an error
  body or log.

---

## 9. Endpoint summary

| Verb | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/edge/devices` | `edge.manage` | Register device + mint enrollment token |
| POST | `/api/v1/edge/devices/enroll` | enrollment token | Exchange token for device credential |
| POST | `/api/v1/edge/devices/{id}:retire` | `edge.manage` | Retire device (revoke credential) |
| POST | `/api/v1/edge/heartbeat` | device cred | Record heartbeat; return liveness + profile pointer |
| GET | `/api/v1/edge/devices/{deviceId}` | `edge.read` / service | Full device (derived status/drift) |
| GET | `/api/v1/edge/devices/{deviceId}/tenant` | service cred | Device→tenant (GEST, FROZEN) |
| GET | `/api/v1/edge/patients/{pacienteId}/device` | `edge.read` / service | Resolve device for patient (PAC) |
| GET | `/api/v1/edge/devices/{deviceId}/liveness` | `edge.read` | Liveness poll (DASH, ETag/304) |
| GET | `/api/v1/edge/patients/{pacienteId}/liveness` | `edge.read` | Liveness poll by patient (DASH) |
| POST | `/api/v1/edge/devices/{id}/pairings` | `edge.manage` | Pair device to patient (exclusive) |
| DELETE | `/api/v1/edge/devices/{id}/pairings/current` | `edge.manage` | Unpair device |
