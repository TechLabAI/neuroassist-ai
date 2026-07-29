# SPEC-05 — Idempotency and Concurrency (`gest-service`)

> **Module:** GEST — Detecção de Gesto (Edge)
> **ADR refs:** ADR-005 BE, ADR-006
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Specify idempotency guarantees, concurrency control strategy, and the handling of duplicate
requests across all GEST operations. Also covers the stateless validate-only ingress for
`RecognizedCommandReport` (the report endpoint is the only cross-service "ingest" surface of GEST).

## 2. Scope

**In:**
- `RecognizedCommandReport` validate-only endpoint: stateless, LGPD privacy invariant, idempotency guidance
- Concurrency control on `TipoGesto` transitions (optimistic lock)
- Concurrency control on `DetectionProfile` publish (unique index contention)
- Idempotency of read-only endpoints (eligibility, profile pull)

**Out:**
- Broker idempotency (messaging disabled in MVP, event-contracts §1)
- EVNT's persistence of the report (owned by EVNT module)

## 3. Report validation — stateless, validate-only

### 3.1 Endpoint

`POST /api/v1/gest/recognized-commands` accepts a `RecognizedCommandReport` and validates it
against the frozen detection contract without persisting anything.

### 3.2 Invariants (EARS)

- THE SYSTEM SHALL treat this endpoint as validate-only (no state mutation, no persistence).
- THE SYSTEM SHALL NOT store any `RecognizedCommandReport` payload, confidence value, video,
  image, or landmark data — privacy invariant (doc 02 / LGPD).
- IF the report's `profileVersion` is not a published version for the tenant, THEN THE SYSTEM
  SHALL respond `422 Unprocessable Entity` (`type: /errors/unknown-profile-version`).
- IF a reported primitive is not a member of the referenced profile version, THEN THE SYSTEM
  SHALL respond `422 Unprocessable Entity` (`type: /errors/primitive-not-in-profile`).
- WHEN `composite = true`, THE SYSTEM SHALL require exactly two entries in `primitives`, and both
  `engineFeatureRef` values SHALL correspond to primitives in the referenced profile.
- THE SYSTEM SHALL require `confidence` in the inclusive range `[0.0, 1.0]`.
- WHEN the validation endpoint receives a duplicate `reportId` within a 24h guidance window, THE
  SYSTEM SHALL return the original validation verdict without additional side effects — guidance
  for EVNT; GEST itself is stateless so duplicates are inherently safe.

### 3.3 Implementation notes

The validator reads only from `detection_profile_primitive` for the referenced `profileVersion`.
No write path exists. The check is a pure in-memory validation against cached profile metadata.

## 4. Concurrency on TipoGesto transitions

- THE SYSTEM SHALL use optimistic locking (`@Version` column on `tipo_gesto`) to detect concurrent
  transitions.
- WHEN two concurrent requests attempt to transition the same `TipoGesto`, the losing transaction
  SHALL receive an `OptimisticLockException`; the service SHALL retry once and then return `409`.
- The audit row in `tipo_gesto_transition` is append-only; concurrent appends do not conflict.

## 5. Concurrency on profile publish

- THE SYSTEM SHALL use the `ux_profile_version` unique constraint and `ux_profile_single_active`
  partial unique index as the last-resort guard against concurrent profile publishes.
- WHEN two concurrent `POST /detection-profiles` arrive, the second TX SHALL fail on the unique
  index; the service SHALL return `409` with a retry-safe message to the conductor.

## 6. Read-only / naturally idempotent endpoints

| Endpoint | Idempotency |
|---|---|
| `/eligibility:check` | Read-only; identical inputs produce identical outputs |
| `GET /detection-profiles/active` | Read-only; conditional GET via ETag |
| `GET /devices/{id}/detection-profile` | Conditional GET (ETag = version); `304` on match |
| `GET /tipos-gesto` | Read-only paginated list |
| `POST /recognized-commands` | Validate-only stateless; `reportId` guidance window |

## 7. Edge cases

| Scenario | Handling |
|---|---|
| Duplicate `reportId` within 24h | Return `200 valid` (GEST is stateless; no stored state to compare) |
| Concurrent transition + read of same TipoGesto | Read observes pre- or post-transition state; no partial state visible (DB serialization) |
| Profile publish while deactivation of prior is in-flight | `ux_profile_single_active` enforces at-most-one atomically |
| `profileVersion` from future (unpublished) | `422 /errors/unknown-profile-version` |

## 8. Acceptance criteria

- THE SYSTEM SHALL validate a `RecognizedCommandReport` without persisting any field.
- Repeated calls to `/recognized-commands` with the same payload produce `200` each time with
  no database side effects.
- A report referencing an unknown `profileVersion` → `422 /errors/unknown-profile-version`.
- A report with `confidence > 1.0` or `confidence < 0.0` → `422`.
- A composite report with one primitive and `composite=true` → `422`.
- Concurrent transitions on the same TipoGesto do not leave the state corrupted (one wins,
  one retries/409).
- No biometric data is present in any DB table or error response after a report validation call.

## 9. BDD coverage

`tests/cucumber/report-validation.feature` — all stateless/idempotency/LGPD scenarios.
