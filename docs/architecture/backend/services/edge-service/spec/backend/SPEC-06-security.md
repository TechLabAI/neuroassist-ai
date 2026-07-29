# SPEC-06 — Security (`edge-service`)

> **Module:** EDGE — Integração de Dispositivo Edge
> **ADR refs:** ADR-005 (per-device authenticated identity, anti-spoofing)
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Define the authentication, authorization, and tenant-isolation model for `edge-service` — including the
**device-credential mechanism decision** that the vision phase deferred (doc 06 G1). Covers OAuth2 JWT
validation, scope-based access, per-device credentials, the enrollment bootstrap, and LGPD boundaries.

## 2. Scope

**In:**
- OAuth2 Bearer JWT: validation, tenant claim, scope enforcement (human + service)
- **Device credential = OAuth2 `client_credentials` per device (JWT device token)** — the G1 decision
- Enrollment bootstrap trust (one-time token) and credential rotation/revocation
- Anti-spoofing (identity from token, never body)
- Roles: `edge.read`, `edge.manage`, `edge.device`, `edge.internal`
- Tenant isolation; LGPD surface

**Out:**
- ACS user-management internals; transport TLS (infra/GWY); mTLS (future hardening, §8)

## 3. Authentication

### 3.1 OAuth2 Bearer JWT (human + service)

- THE SYSTEM SHALL require a valid OAuth2 Bearer JWT on every endpoint except `/actuator/health`,
  `/actuator/info`, and `POST /devices/enroll` (enrollment-token guarded).
- THE SYSTEM SHALL validate the token signature against the configured `jwk-set-uri` (ACS).
- IF the token is expired, malformed, or missing on a guarded endpoint, THEN THE SYSTEM SHALL respond
  `401 Unauthorized`.
- THE SYSTEM SHALL derive `tenantId` exclusively from the token `tenant` claim.
- IF a request body carries a `tenantId` differing from the token claim, THEN THE SYSTEM SHALL respond
  `403 Forbidden` (`type: /errors/identity-mismatch`).

### 3.2 Device credential mechanism — DECISION (closes doc 06 G1)

- THE SYSTEM SHALL credential each device as an **OAuth2 `client_credentials` client**: one `clientId` +
  hashed `clientSecret` per `DispositivoEdge`, issued at enrollment and rotated on re-pairing/retire.
- THE SYSTEM SHALL require the device to obtain a short-lived **device access token** (JWT, scope
  `edge.device`, claims `deviceId` + `tenant`) from ACS's token endpoint, presented as `Authorization:
  Bearer` on heartbeat calls.
- THE SYSTEM SHALL resolve the acting `deviceId`/`tenant` from those claims only — never the body
  (anti-spoofing, ADR-005 invariant #2, doc 06 R3).
- **Alignment (no rework):** this matches the JWT-shaped device token GEST assumed provisionally (GEST
  `api-contract.md` §6.3, `SPEC-06` §3.2). The GEST open item is closed here with `client_credentials`.

### 3.3 Enrollment bootstrap trust (closes doc 06 G4)

- THE SYSTEM SHALL mint a one-time, TTL-bounded enrollment token at registration (default 900 s),
  stored **hashed** (`dispositivo_enrollment`).
- WHEN a valid, unexpired, unconsumed token is presented at `/devices/enroll`, THE SYSTEM SHALL issue the
  device credential (returning the secret exactly once) and mark the token consumed — atomically.
- IF the token is unknown, expired, or consumed, THEN THE SYSTEM SHALL respond
  `401 /errors/enrollment-invalid` without revealing which condition failed.

### 3.4 Credential lifecycle

- WHEN a device is retired or re-paired, THE SYSTEM SHALL revoke the superseded credential
  (`status='revoked'`, `revoked_at` stamped) so a stale secret can never authenticate.
- THE SYSTEM SHALL store only hashes; no plaintext secret or token is ever persisted or logged.

## 4. Authorization — scope-per-operation table

| Operation | Required scope |
|---|---|
| Read device / liveness (`GET /devices/{id}`, `/liveness`, `/patients/{id}/device`) | `edge.read` |
| Register / retire / pair / unpair | `edge.manage` (conductor role) |
| Heartbeat | `edge.device` (device token) |
| Enroll | one-time enrollment token (no Bearer) |
| Device→tenant / internal resolve (GEST, PAC) | `edge.internal` (service) or `edge.read` |

- WHERE the caller's token lacks the required scope, THE SYSTEM SHALL respond `403 /errors/insufficient-scope`.

## 5. Tenant isolation

- THE SYSTEM SHALL scope every database query to the tenant schema derived from the token `tenant` claim
  (`SchemaRoutingDataSource`).
- THE SYSTEM SHALL NOT return resources belonging to a different tenant.
- WHEN a cross-tenant access is attempted via a direct id (including a service token from another tenant),
  THE SYSTEM SHALL respond `404` rather than `403`, to avoid tenant/device enumeration.

## 6. LGPD compliance

EDGE stores device metadata plus a `paciente_id` reference (personal by association), inside the
per-empresa schema:

- THE SYSTEM SHALL NOT store or echo any video, image, landmark, or biometric datum (privacy invariant,
  doc 02 / Framing #6).
- WHERE patient erasure is exercised, THE SYSTEM SHALL clear the `paciente_id` link and unpair/retire the
  device; the device row itself carries no personal content beyond that link (doc 05 §LGPD).
- THE SYSTEM SHALL include `correlationId` in error bodies but SHALL NOT echo credential secrets or
  patient-identifying data.

## 7. Observability / audit note

All authenticated requests produce an access log with `correlationId`, hashed `tenantId`, and the HTTP
outcome. Pairing and liveness crossings are audited in `dispositivo_edge_transition` (safety evidence).
No raw PII, credential secret, or biometric-adjacent data is logged. See SPEC-07.

## 8. Future hardening (deferred, not MVP)

- **mTLS per device** (client certificate) is recorded as a future alternative to the `client_credentials`
  device token, for stronger device-binding when a PKI/MDM is available. It would add credential columns
  via a new `V07xx` migration and a certificate verifier; **out of MVP scope** (doc 06 G1). The GEST/EDGE
  joint decision (GEST `integration-contracts.md` §1.3) is hereby resolved as `client_credentials` for
  the MVP, with mTLS as the documented upgrade path.

## 9. Acceptance criteria

- Unauthenticated request to any guarded endpoint → `401`.
- Caller with `edge.read` trying to register/pair → `403 /errors/insufficient-scope`.
- Heartbeat with a body `deviceId` ≠ token → `403 /errors/identity-mismatch`.
- Re-used/expired enrollment token → `401 /errors/enrollment-invalid`.
- Retired device's old credential cannot authenticate a heartbeat.
- Service token from tenant B cannot resolve tenant A's device (`404`).
- No credential secret or `paciente_id` appears in any log or error body.
