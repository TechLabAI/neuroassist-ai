# SPEC-01 — API Surface (`gest-service`)

> **Module:** GEST — Detecção de Gesto (Edge) · **Service:** `gest-service`
> **ADR refs:** ADR-005 (edge/cloud split), ADR-006 (detectability lifecycle)
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Define the full public REST surface of `gest-service`, its versioning strategy, mandatory headers,
error convention, and the scope-per-operation authorization table. This spec is the authoritative
index: every endpoint is described in full detail in `contracts/api-contract.md`; this doc is the
surface map and the acceptance-criteria gate.

## 2. Scope

**In:**
- All REST endpoints under `/api/v1/gest/` served by `gest-service`
- Auth/headers convention; RFC 7807 ProblemDetail catalog
- EARS invariants on the API surface layer

**Out:**
- Internal service logic (see SPEC-02, SPEC-03)
- Data model DDL (see SPEC-04)
- Security details beyond scopes (see SPEC-06)

## 3. Technical decisions

| Decision | ADR |
|---|---|
| `gest-service` is cloud-side detection authority; no pixel ingestion | ADR-005 BE |
| Detectability lifecycle driven exclusively via `/transitions` endpoint | ADR-006 |
| Detection contract versioned independently of URI (`X-Detection-Contract-Version`) | api-contract §1 |
| Edge authentication via JWT-shaped device token (transport TBD with EDGE) | api-contract §6.3 |

## 4. Endpoint inventory

| # | Verb | Path | Scope | SPEC ref |
|---|---|---|---|---|
| 1 | GET | `/api/v1/gest/tipos-gesto` | `gest.read` | §4.1 |
| 2 | GET | `/api/v1/gest/tipos-gesto/{id}` | `gest.read` | §4.1 |
| 3 | POST | `/api/v1/gest/tipos-gesto` | `gest.manage` | §4.2 |
| 4 | POST | `/api/v1/gest/tipos-gesto/{id}/transitions` | `gest.manage` | §4.2 |
| 5 | POST | `/api/v1/gest/eligibility:check` | `gest.read` | SPEC-03 |
| 6 | POST | `/api/v1/gest/detection-profiles` | `gest.manage` | §4.3 |
| 7 | GET | `/api/v1/gest/detection-profiles/active` | `gest.read` | §4.3 |
| 8 | GET | `/api/v1/gest/detection-profiles/{version}` | `gest.read` | §4.3 |
| 9 | GET | `/api/v1/gest/devices/{deviceId}/detection-profile` | device cred | §4.3 |
| 10 | GET | `/api/v1/gest/detection-contract` | `gest.read` | §4.4 |
| 11 | POST | `/api/v1/gest/recognized-commands` | device cred | SPEC-05 |

## 4.1 Read endpoints (catalog, profiles, contract)

### Invariants (EARS)

- THE SYSTEM SHALL return all responses under `/api/v1/gest/` with the header
  `X-Detection-Contract-Version: 1.0.0` (current frozen contract).
- THE SYSTEM SHALL derive `tenantId` exclusively from the JWT `tenant` claim.
- IF a request body carries a `tenantId` field that differs from the JWT claim, THEN THE SYSTEM
  SHALL reject the request with `403 Forbidden` (`type: /errors/tenant-mismatch`).
- WHEN a request omits `X-Correlation-Id`, THE SYSTEM SHALL generate one and return it in the
  response.
- THE SYSTEM SHALL use 0-based page numbering; default `size=50`, max `size=200`.

### Edge-cases

- Unknown `{id}` on single-resource fetch → `404` with appropriate `type`.
- Unknown `{version}` on profile by version → `404`.
- Unauthenticated request → `401 Unauthorized`.

## 4.2 Mutation endpoints (registration, lifecycle transitions, profile publish)

### Invariants (EARS)

- WHERE the caller lacks the `gest.manage` scope, THE SYSTEM SHALL respond `403 Forbidden`
  (`type: /errors/insufficient-scope`).
- THE SYSTEM SHALL create every new primitive in state `registered` regardless of request content.
- IF `nome` is blank or longer than 120 chars, THEN THE SYSTEM SHALL respond `400 Bad Request`.
- IF a non-deprecated primitive with the same `lower(nome)` already exists in the tenant, THEN
  THE SYSTEM SHALL respond `409 Conflict` (`type: /errors/tipo-gesto-duplicate`).
- IF `toState` is not reachable from the current state, THEN THE SYSTEM SHALL respond `409 Conflict`
  (`type: /errors/illegal-transition`) and SHALL NOT mutate state.
- IF `toState = supported` AND `engineFeatureRef` is null, THEN THE SYSTEM SHALL respond
  `422 Unprocessable Entity` (`type: /errors/engine-feature-ref-required`).

## 4.3 Detection-profile endpoints

### Invariants (EARS)

- WHEN a new detection profile is published, THE SYSTEM SHALL include only primitives in state
  `supported` at publish time.
- THE SYSTEM SHALL assign a strictly monotonic, never-reused `version` per tenant.
- WHEN a new profile becomes active, THE SYSTEM SHALL set every prior profile to `active: false`
  without mutating their content.
- IF the tenant has zero `supported` primitives, THEN THE SYSTEM SHALL respond `422` (`type:
  /errors/empty-profile`).
- WHEN the edge pulls with `If-None-Match` equal to the active profile's ETag (version), THE SYSTEM
  SHALL respond `304 Not Modified` with no body.

## 4.4 Detection contract descriptor

### Invariants (EARS)

- THE SYSTEM SHALL expose the current contract version at `GET /api/v1/gest/detection-contract`
  and SHALL keep it in sync with `X-Detection-Contract-Version` on every response.

## 5. Error catalog (RFC 7807 ProblemDetail)

See `contracts/api-contract.md` §7 for the full table. Every error response:
- Uses `application/problem+json`.
- Includes `correlationId` (from `X-Correlation-Id`).
- THE SYSTEM SHALL NOT include biometric, video, image, or landmark data in any error body or log.

## 6. Edge cases

| Scenario | Behavior |
|---|---|
| Tenant has no supported primitives | `POST /detection-profiles` → `422 /errors/empty-profile` |
| `If-None-Match` matches active profile version | `GET /devices/{id}/detection-profile` → `304` |
| Device tenant mismatch | `403 /errors/device-not-paired` |
| Body `tenantId` ≠ JWT `tenant` | `403 /errors/tenant-mismatch` |
| Empty string `nome` | `400 /errors/validation` |

## 7. Acceptance criteria

- THE SYSTEM SHALL route requests at `/api/v1/gest/**` through the GWY (`neuroassist-api-gateway`)
  and SHALL register as a discoverable service in REG (`neuroassist-service-registry`).
- THE SYSTEM SHALL respond with `application/json` for all successful responses.
- THE SYSTEM SHALL respond with `application/problem+json` for all error responses.
- IF the OAuth2 token is expired, malformed, or missing on a non-probe endpoint, THEN THE SYSTEM
  SHALL respond `401 Unauthorized`.
- THE SYSTEM SHALL include `X-Correlation-Id` in every response (echoed or generated).
- THE SYSTEM SHALL include `X-Detection-Contract-Version: 1.0.0` in every response.

## 8. BDD coverage

See `tests/cucumber/tipo-gesto-lifecycle.feature`, `tests/cucumber/eligibility-check.feature`,
`tests/cucumber/detection-profile.feature`, `tests/cucumber/report-validation.feature`.
