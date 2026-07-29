# SPEC-01 — API Surface (`edge-service`)

> **Module:** EDGE — Integração de Dispositivo Edge · **Service:** `edge-service`
> **ADR refs:** ADR-005 (pairing & identity), ADR-006 (heartbeat & liveness)
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Define the full public REST surface of `edge-service`, its versioning strategy, mandatory headers, error
convention, and the scope-per-operation authorization table. Every endpoint is described in full in
`contracts/api-contract.md`; this doc is the surface map and the acceptance-criteria gate.

## 2. Scope

**In:**
- All REST endpoints under `/api/v1/edge/` served by `edge-service`
- Auth/headers convention; RFC 7807 ProblemDetail catalog
- EARS invariants on the API surface layer

**Out:**
- Internal service logic (SPEC-02, SPEC-03, SPEC-11)
- Data model DDL (SPEC-04)
- Security details beyond scopes (SPEC-06)

## 3. Technical decisions

| Decision | Ref |
|---|---|
| `edge-service` is the backend device authority; no pixel ingestion | ADR-005/006 |
| Acting `deviceId` always from the authenticated token, never the body | api-contract §1.1, ADR-005 |
| Liveness derived at read time; no persisted `status` | ADR-006 |
| Device auth = OAuth2 `client_credentials` per device (JWT device token) | api-contract §6.3 |
| Registration operator-driven + one-time enrollment token | api-contract §2 |

## 4. Endpoint inventory

| # | Verb | Path | Scope | SPEC ref |
|---|---|---|---|---|
| 1 | POST | `/api/v1/edge/devices` | `edge.manage` | SPEC-11 |
| 2 | POST | `/api/v1/edge/devices/enroll` | enrollment token | SPEC-11 |
| 3 | POST | `/api/v1/edge/devices/{id}:retire` | `edge.manage` | SPEC-11 |
| 4 | POST | `/api/v1/edge/heartbeat` | device cred | SPEC-02 |
| 5 | GET | `/api/v1/edge/devices/{deviceId}` | `edge.read` / service | SPEC-02 |
| 6 | GET | `/api/v1/edge/devices/{deviceId}/tenant` | service cred | SPEC-10 |
| 7 | GET | `/api/v1/edge/patients/{pacienteId}/device` | `edge.read` / service | SPEC-03 |
| 8 | GET | `/api/v1/edge/devices/{deviceId}/liveness` | `edge.read` | SPEC-02 |
| 9 | GET | `/api/v1/edge/patients/{pacienteId}/liveness` | `edge.read` | SPEC-02 |
| 10 | POST | `/api/v1/edge/devices/{id}/pairings` | `edge.manage` | SPEC-03 |
| 11 | DELETE | `/api/v1/edge/devices/{id}/pairings/current` | `edge.manage` | SPEC-03 |

## 4.1 Read endpoints (device, liveness, tenant resolution)

### Invariants (EARS)

- THE SYSTEM SHALL derive `tenantId` exclusively from the caller's token claims.
- THE SYSTEM SHALL compute `status` and `profileDrift` at read time; they are not stored columns.
- WHEN a request omits `X-Correlation-Id`, THE SYSTEM SHALL generate one and return it.
- THE SYSTEM SHALL expose the effective `stalenessThresholdSeconds` on liveness/heartbeat responses.
- WHEN a liveness poll sends `If-None-Match` equal to the current status-bucket ETag, THE SYSTEM SHALL
  respond `304 Not Modified` with no body.

### Edge-cases

- Unknown `{deviceId}` → `404 /errors/device-not-found`.
- Patient with no paired device → `404 /errors/patient-not-paired`.
- Unauthenticated request on a non-probe, non-enroll endpoint → `401 Unauthorized`.

## 4.2 Mutation endpoints (register, enroll, pair, unpair, retire, heartbeat)

### Invariants (EARS)

- WHERE the caller lacks the `edge.manage` scope on an operator endpoint, THE SYSTEM SHALL respond
  `403 Forbidden` (`type: /errors/insufficient-scope`).
- THE SYSTEM SHALL create every new device in state `registered` regardless of request content.
- THE SYSTEM SHALL derive the acting `deviceId` on heartbeat/enroll from the token, never the body.
- IF the body carries a `deviceId`/`tenantId` differing from the authenticated principal, THEN THE SYSTEM
  SHALL respond `403 Forbidden` (`type: /errors/identity-mismatch`).
- IF the patient already has a `paired` device, THEN THE SYSTEM SHALL respond `409 /errors/pairing-conflict`.
- IF an operation targets a `retired` device, THEN THE SYSTEM SHALL respond `409 /errors/device-retired`.

## 5. Error catalog (RFC 7807 ProblemDetail)

See `contracts/api-contract.md` §8 for the full table. Every error response:
- Uses `application/problem+json`.
- Includes `correlationId` (from `X-Correlation-Id`).
- THE SYSTEM SHALL NOT include biometric, video, image, landmark, or credential secret in any error body
  or log.

## 6. Edge cases

| Scenario | Behavior |
|---|---|
| Enrollment token expired/consumed | `401 /errors/enrollment-invalid` |
| Heartbeat body `deviceId` ≠ token | `403 /errors/identity-mismatch` |
| Second device paired to same patient | `409 /errors/pairing-conflict` |
| Liveness poll, unchanged status bucket | `304 Not Modified` |
| GEST unavailable during heartbeat | `200` + `profilePointerDegraded: true` |
| Cross-tenant service token on device read | `404` (no enumeration) |

## 7. Acceptance criteria

- THE SYSTEM SHALL route requests at `/api/v1/edge/**` through GWY (`neuroassist-ai-api-gateway`) and
  SHALL register as a discoverable service in REG (`neuroassist-ai-service-registry`).
- THE SYSTEM SHALL respond with `application/json` for successful responses and
  `application/problem+json` for errors.
- IF the OAuth2 token is expired, malformed, or missing on a non-probe, non-enroll endpoint, THEN THE
  SYSTEM SHALL respond `401 Unauthorized`.
- THE SYSTEM SHALL include `X-Correlation-Id` in every response (echoed or generated).

## 8. BDD coverage

See `tests/cucumber/device-registration-enrollment.feature`, `tests/cucumber/device-pairing.feature`,
`tests/cucumber/heartbeat-liveness.feature`, `tests/cucumber/profile-pointer-drift.feature`.
