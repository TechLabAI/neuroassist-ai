# SPEC-06 — Security (`gest-service`)

> **Module:** GEST — Detecção de Gesto (Edge)
> **ADR refs:** ADR-005 BE (edge/cloud split), ADR-006
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Define the authentication, authorization, and tenant-isolation model for `gest-service`. Covers
OAuth2 JWT validation, scope-based access control, edge device credentials, and LGPD compliance
boundaries.

## 2. Scope

**In:**
- OAuth2 Bearer JWT: validation, tenant claim, scope enforcement
- Roles: `gest.read`, `gest.manage`, `device` (edge credential)
- Tenant isolation: every operation scoped to the JWT `tenant` claim
- LGPD surface: GEST is explicitly outside the sensitive biometric surface
- Edge device credential (provisional JWT-device token; mTLS vs `client_credentials` deferred)

**Out:**
- ACS (authentication/user management) implementation details
- EDGE pairing contract implementation (deferred)
- Transport-level TLS (infra/GWY responsibility)

## 3. Authentication

### 3.1 OAuth2 Bearer JWT

- THE SYSTEM SHALL require a valid OAuth2 Bearer JWT on every endpoint except health/observability
  probes (`/actuator/health`, `/actuator/info`).
- THE SYSTEM SHALL validate the token signature against the configured `jwk-set-uri` (ACS).
- IF the token is expired, malformed, or missing, THEN THE SYSTEM SHALL respond `401 Unauthorized`.
- THE SYSTEM SHALL derive `tenantId` exclusively from the JWT `tenant` claim.
- IF a request body carries a `tenantId` that differs from the JWT `tenant` claim, THEN THE SYSTEM
  SHALL respond `403 Forbidden` (`type: /errors/tenant-mismatch`).

### 3.2 Edge device credential (provisional)

- THE SYSTEM SHALL accept a JWT-shaped device token for the edge pull (`§4.4`) and report
  validation (`§5.2`) endpoints. The device token carries `deviceId` and `tenant` claims.
- IF the device token's `tenant` claim does not match the target profile's tenant, THEN THE SYSTEM
  SHALL respond `403 Forbidden` (`type: /errors/device-not-paired`).
- **Open item (doc 06 Gaps):** the final device credential shape (mTLS vs. `client_credentials`
  per device) is deferred to a joint GEST/EDGE/edge-team decision. The JWT-device assumption is
  provisional and will be revised in backlog.

## 4. Authorization — scope-per-operation table

| Operation | Required scope |
|---|---|
| List / get TipoGesto, eligibility, get profile, get contract | `gest.read` |
| Register primitive, transition lifecycle, publish profile | `gest.manage` (conductor role) |
| Edge profile pull, report validation | device credential (`deviceId` + `tenant` claims) |

- WHERE the caller's token lacks the required scope, THE SYSTEM SHALL respond `403 Forbidden`
  (`type: /errors/insufficient-scope`).

## 5. Tenant isolation

- THE SYSTEM SHALL scope every database query to the tenant schema derived from the JWT `tenant`
  claim (`SchemaRoutingDataSource`).
- THE SYSTEM SHALL NOT return resources belonging to a different tenant in any response.
- WHEN a cross-tenant access is attempted via a direct id reference, THE SYSTEM SHALL respond as
  if the resource does not exist (`404`) rather than `403` to avoid tenant enumeration.

## 6. LGPD compliance

GEST is **outside the LGPD sensitive-data surface** (privacy invariant, doc 02 / doc 05):

- No column, payload, error body, or log entry may contain video, image, landmark, biometric
  datum, or confidence value from real-world sensor data.
- THE SYSTEM SHALL NOT store any `RecognizedCommandReport` content in any database table.
- THE SYSTEM SHALL include the `correlationId` in every error body but SHALL NOT echo input fields
  that could contain patient-identifying data.
- WHERE LGPD data-subject rights are exercised against GEST, the tenant administrator SHALL find
  no personal data in GEST tables (only abstract primitive identifiers and opaque engine handles).

## 7. Observability / audit note

All authenticated requests produce an access log entry with `correlationId`, `tenantId` (hashed),
and the HTTP outcome. No raw user PII, device location, or biometric-adjacent data is logged.
See SPEC-07 for observability configuration.

## 8. Acceptance criteria

- Unauthenticated request to any business endpoint → `401`.
- Expired JWT → `401`.
- Caller with `gest.read` tries to register a primitive → `403`.
- Caller with `gest.manage` from tenant A cannot read tenant B's primitives.
- Edge device token with mismatched tenant → `403 /errors/device-not-paired`.
- No field from `RecognizedCommandReport` (confidence, timingWindow) appears in any log or error
  body after a validation call.
