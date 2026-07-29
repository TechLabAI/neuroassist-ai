---
module: EDGE
service: edge-service
package: com.neuroassist.edge
version: "1.0.0"
status: specs-complete
port: 8087
adrs:
  - ADR-005-device-pairing-identity-model.md
  - ADR-006-heartbeat-liveness-profile-delivery.md
generatedAt: "2026-07-29"
---

# edge-service Spec Entry Point

> Module: **EDGE — Integração de Dispositivo Edge** · Service: `edge-service`
> Package: `com.neuroassist.edge` · Multi-tenant: schema-per-empresa · Messaging: disabled (sync REST only)
> Pipeline phase: **specs** complete (contracts + SPECs + Gherkin + C4).

## Purpose

`edge-service` is the backend **device authority** of NeuroAssist AI. It owns:
- The identity of each edge agent (`DispositivoEdge`) and its per-device credential
- Exclusive pairing of a device to one patient (one active device per patient, MVP)
- Heartbeat ingestion and **read-derived liveness** (`online`/`offline` from staleness, no persisted flag)
- Delivery of the current detection-profile **version pointer** (authored by GEST) + drift detection

It never receives a pixel, image, or landmark — only device metadata, version pointers, and a
`paciente_id` reference. It runs no computer vision and authors no detection profile.

## Specs-phase decisions (closing the vision gaps)

| Vision gap | Decision frozen in Specs | Where |
|---|---|---|
| G1 — device credential mechanism | **OAuth2 `client_credentials` per device** (JWT device token, `deviceId`+`tenant` claims); mTLS deferred | api-contract §6.3, SPEC-06 |
| G2 — staleness threshold | **90 s** = `3 × 30 s` heartbeat (`staleness-factor`), configurable | api-contract §5, SPEC-02 |
| G3 — heartbeat / poll interval | heartbeat **30 s**; DASH poll hint **15 s**; worst-case detection ≈ **105 s** | api-contract §5, SPEC-02 |
| G4 — bootstrap trust | **one-time, 15-min enrollment token** (claim code), single-use, hash-stored | api-contract §2, SPEC-11 |

The G1 decision is **aligned with GEST's provisional assumption** (JWT-shaped device token) so no GEST
rework is required (GEST `api-contract.md` §6.3). GEST's already-frozen inbound expectation
`GET /api/v1/edge/devices/{deviceId}/tenant` (GEST `integration-contracts.md` §1) is honored verbatim
(api-contract §4.2).

## Document map

| Document | Path | Status |
|---|---|---|
| Vision (01-06) | `01..06-*.md` | complete (M1) |
| ADR-005 pairing & identity | `adr/ADR-005-device-pairing-identity-model.md` | complete (M1) |
| ADR-006 heartbeat & liveness | `adr/ADR-006-heartbeat-liveness-profile-delivery.md` | complete (M1) |
| **API contract** | `contracts/api-contract.md` | complete (M2) |
| **Data model** | `contracts/data-model.md` | complete (M2) |
| **Event contracts** | `contracts/event-contracts.md` | complete (M2) |
| Hub integration | `contracts/hub-integration.md` | complete (M2) |
| Integration contracts | `contracts/integration-contracts.md` | complete (M2) |
| SPEC-01 API surface | `spec/backend/SPEC-01-api-surface.md` | complete |
| SPEC-02 Heartbeat & liveness | `spec/backend/SPEC-02-heartbeat-liveness-profile-delivery.md` | complete |
| SPEC-03 Pairing lifecycle | `spec/backend/SPEC-03-pairing-lifecycle-exclusivity.md` | complete |
| SPEC-04 Data model/migrations | `spec/backend/SPEC-04-data-model-migrations.md` | complete |
| SPEC-05 Idempotency/concurrency | `spec/backend/SPEC-05-idempotency-concurrency.md` | complete |
| SPEC-06 Security | `spec/backend/SPEC-06-security.md` | complete |
| SPEC-07 Observability/health | `spec/backend/SPEC-07-observability-health.md` | complete |
| SPEC-08 BDD scenarios | `spec/backend/SPEC-08-bdd-test-scenarios.md` | complete |
| SPEC-09 Messaging | `spec/backend/SPEC-09-messaging-architecture.md` | complete |
| SPEC-10 Integrations | `spec/backend/SPEC-10-integrations.md` | complete |
| SPEC-11 Registration & enrollment | `spec/backend/SPEC-11-registration-enrollment.md` | complete |
| Gherkin features (4) | `tests/cucumber/*.feature` | complete |
| Event catalog | `events/event-catalog.md` | complete |
| C4 diagrams | `diagrams/c4-{context,container,component}.puml` | complete |
| Setup local | `setup-local.md` | complete |

## Key invariants at a glance

| # | Invariant | Enforcement |
|---|---|---|
| 1 | Exclusive pairing — one `paired` device per patient | `ux_dispositivo_edge_active_pairing` (partial UNIQUE) + `409 pairing-conflict` |
| 2 | Authenticated identity — reporting id from token, never body | JWT/device-token claims; `403 identity-mismatch` |
| 3 | Deterministic liveness — pure function of freshness + threshold | Read-time derivation (no `status` column) |
| 4 | No silent change — pairing & liveness crossings audited | `dispositivo_edge_transition` append-only |
| 5 | Referential honesty for PAC | `GET /patients/{id}/device` returns paired-or-404 |
| 6 | No media, ever | Heartbeat carries only liveness/version metadata (LGPD) |

## Next step

`/be-architect-backlog neuroassist-ai EDGE`
