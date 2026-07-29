# SPEC-10 — Integrations (`edge-service`)

> **Module:** EDGE — Integração de Dispositivo Edge
> **ADR refs:** ADR-005, ADR-006
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Specify EDGE's integrations: outbound to GEST (profile pointer), inbound from GEST (device→tenant), PAC
(device validation), DASH (liveness poll), and the edge agent (in-product REST client). All synchronous
REST (messaging disabled). Full contract detail: `contracts/integration-contracts.md` and `hub-integration.md`.

## 2. Scope

**In:**
- Outbound `DetectionProfilePort` → GEST (`GET /gest/detection-profiles/active`) + resilience/degradation
- Inbound `GET /devices/{id}/tenant` (GEST — frozen), `GET /patients/{id}/device` (PAC),
  `GET /devices/{id}/liveness` (DASH)
- The edge agent client (register/enroll/heartbeat)

**Out:**
- Third-party APIs (none in MVP); credential mechanism (SPEC-06); event bus (SPEC-09)

## 3. Outbound — GEST profile pointer (EARS)

- WHEN a heartbeat is accepted, THE SYSTEM SHALL fetch the active profile version from GEST via
  `DetectionProfilePort` and compute `currentProfileVersion`/`profileDrift`.
- THE SYSTEM SHALL apply a Resilience4j circuit breaker (50% / 10-call window, 30 s to HALF_OPEN),
  2 000 ms read / 500 ms connect timeout, 1 retry on connect/read timeout (idempotent GET).
- IF GEST is unavailable, THEN THE SYSTEM SHALL accept the heartbeat with `profilePointerDegraded: true`
  and SHALL NOT fail liveness.
- THE SYSTEM SHALL forward `X-Correlation-Id` and SHALL send no biometric/patient data to GEST.

## 4. Inbound — GEST device→tenant (frozen) (EARS)

The endpoint GEST already froze against EDGE (GEST `integration-contracts.md` §1).

- THE SYSTEM SHALL expose `GET /api/v1/edge/devices/{deviceId}/tenant` returning
  `{ deviceId, tenantRef }` for a registered device, `404 /errors/device-not-found` otherwise.
- THE SYSTEM SHALL authenticate the caller with a service credential (`edge.internal`) and return `404`
  for a cross-tenant token (no enumeration).
- THE SYSTEM SHALL return only `deviceId` + `tenantRef` — no patient, liveness, or credential data.

## 5. Inbound — PAC device validation (EARS)

- THE SYSTEM SHALL expose `GET /api/v1/edge/patients/{pacienteId}/device` returning the currently paired
  device or `404 /errors/patient-not-paired` (referential honesty, invariant #5).
- THE SYSTEM SHALL NOT require PAC to send patient data beyond the id in the URL.

## 6. Inbound — DASH liveness poll (EARS)

- THE SYSTEM SHALL expose `GET /api/v1/edge/devices/{deviceId}/liveness` (and the by-patient variant)
  with read-time-derived `status` and ETag/`304` support (SPEC-02 §6).
- THE SYSTEM SHALL include `stalenessThresholdSeconds` + `serverTime` so DASH interprets `status`
  consistently.

## 7. Inbound — edge agent (in-product client) (EARS)

- THE SYSTEM SHALL treat the edge agent as an in-product REST client (not a third party), reachable only
  via GWY, authenticating as the device on every steady-state call (SPEC-06).
- THE SYSTEM SHALL accept from the agent only liveness/version metadata — never media.

## 8. No third-party integrations (MVP)

- No push-notification vendor, no MDM. Future candidates (remote alerting, fleet management, mTLS PKI)
  are recorded in SPEC-06 §8 and `integration-contracts.md` §5.

## 9. Acceptance criteria

- A heartbeat returns the correct `currentProfileVersion`/`profileDrift` when GEST is up, and
  `profilePointerDegraded: true` when GEST is down — liveness unaffected.
- GEST's `GET /devices/{id}/tenant` returns exactly `{ deviceId, tenantRef }` or `404`.
- PAC's resolve returns paired-or-404; DASH's poll derives status at read time with ETag support.
- No cross-tenant call ever leaks another tenant's device.
