# SPEC-05 — Idempotency & Concurrency (`edge-service`)

> **Module:** EDGE — Integração de Dispositivo Edge
> **ADR refs:** ADR-005, ADR-006
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Specify how `edge-service` behaves under retries and concurrent mutations: idempotent heartbeats,
single-use enrollment, exclusive-pairing race handling, and optimistic concurrency on the aggregate.

## 2. Scope

**In:**
- Heartbeat idempotency (repeatable, liveness-refresh only)
- Enrollment single-use semantics
- Concurrent pairing races (DB unique + optimistic lock)
- Optimistic concurrency via `row_version`

**Out:**
- Transport retries at GEST (integration-contracts §1.1); DASH poll cadence (SPEC-02)

## 3. Idempotency (EARS)

- THE SYSTEM SHALL treat `POST /heartbeat` as idempotent for liveness: repeated heartbeats within a
  window re-stamp `ultima_sincronizacao`; only a `status` **crossing** appends an audit row (so duplicate
  beats do not grow the trail).
- THE SYSTEM SHALL treat all reads (`GET /devices/{id}`, `/tenant`, `/patients/{id}/device`,
  `/liveness`) as idempotent (no state mutation).
- WHEN enrollment (`POST /devices/enroll`) is retried with an already-consumed token, THE SYSTEM SHALL
  respond `401 /errors/enrollment-invalid` and SHALL NOT issue a second credential (single-use).
- WHEN registration is retried, THE SYSTEM SHALL create a distinct device (registration is not idempotent
  by design — each call mints a new identity); the operator UI SHALL avoid double submits.

## 4. Concurrency (EARS)

- THE SYSTEM SHALL guard every `dispositivo_edge` mutation with optimistic locking via `row_version`
  (JPA `@Version`).
- IF a mutation is applied against a stale `row_version`, THEN THE SYSTEM SHALL respond
  `409 /errors/optimistic-lock` and SHALL NOT partially apply the change.
- WHEN two operators pair different devices to the same patient concurrently, THE SYSTEM SHALL allow at
  most one to succeed — the second fails on `ux_dispositivo_edge_active_pairing` and THE SYSTEM SHALL map
  the constraint violation to `409 /errors/pairing-conflict`.
- WHEN a heartbeat and a liveness-read observe the same `offline→online` crossing concurrently, THE
  SYSTEM SHALL append at most one `liveness` transition for that crossing (idempotent crossing detection).

## 5. Transaction boundaries

- THE SYSTEM SHALL commit enrollment token consumption and credential issuance in one transaction
  (data-model §2.2/§2.3) — no window where a token is consumed without a credential.
- THE SYSTEM SHALL raise domain events **post-commit** (event-contracts §2) so no consumer observes an
  uncommitted state.

## 6. Edge cases

| Scenario | Behavior |
|---|---|
| Duplicate heartbeat (same second) | both accepted; single re-stamp; no extra audit |
| Concurrent pair to same patient | one `201`, one `409 pairing-conflict` |
| Stale-write pairing update | `409 optimistic-lock` |
| Enroll retried after success | `401 enrollment-invalid` |

## 7. Acceptance criteria

- Under 100 concurrent pairings to one patient, exactly one succeeds; the rest are `409`.
- A replayed enrollment never yields a second credential.
- A stale `row_version` update never partially mutates the aggregate.
- Repeated heartbeats never inflate the transition audit beyond actual crossings.

## 8. BDD coverage

Concurrency scenarios are asserted in `tests/cucumber/device-pairing.feature` (exclusivity) and
`tests/cucumber/device-registration-enrollment.feature` (single-use enrollment).
