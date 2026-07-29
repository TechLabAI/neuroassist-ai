# SPEC-08 — BDD Test Scenarios

> Service: `evnt-service` · Prose companion to the pure Gherkin in `tests/cucumber/*.feature`.
> The `.feature` files are the machine-consumable source (`be-bdd-generator.py`); this file documents the
> reusable steps, fixtures, and coverage mapping.

## Feature files
| File | Covers | SPEC / ADR |
|---|---|---|
| `report-ingress-idempotency.feature` | ingress, idempotency, GEST rejection, biometric reject, tenant match | SPEC-01/04/05, ADR-004 |
| `urgency-tiered-notification.feature` | tier derivation, durable-first, fail-open retry, boot sweep | SPEC-02, ADR-005 |
| `caregiver-confirmation-feedback.feature` | terminal verdict, attribution, HIST, tenant guard | SPEC-02, ADR-006 |
| `live-event-delivery.feature` | SSE push, hydration, `Last-Event-ID` resume, poll fallback, keepalive, tenant scope | SPEC-03, FE ADR-005 |

## Reusable steps (glue)
- `Given a tenant "<t>" with patient "<p>"` — provisions a tenant schema + patient reference stub (PAC mock).
- `Given a valid device token scoped to tenant "<t>" and patient "<p>"` — mints an interim JWT device token.
- `Given a caregiver session for "<cg>" authorized for patient "<p>"` — OAuth2 resource-server test token.
- `Given GEST will validate/reject …` — WireMock stub for `gest-service`.
- `Given ACS resolves "<cg>" as the responsible caregiver` — WireMock stub for `acs-service`.
- `Given DASH is unavailable / will fail then succeed` — WireMock fault injection for `dash-service`.
- `When the edge posts the report to "/api/v1/reports"` — REST call with device token.
- `When the retry sweep runs` / `When evnt-service restarts` — trigger the dispatch sweep.
- `Then the caregiver's stream receives a "<event>" frame` — SSE client assertion.

## Fixtures / example data
- Commands: `cmd-emergency` (`urgencia=emergencia`), `cmd-routine` (`urgencia=rotina`).
- Caregiver `cg-1` (routing target for `pac-1`), tenants `empresa_demo` / `empresa_other`.
- Reports use deterministic `reportId`s (`r-100`, `u-1`, …) for idempotency assertions.

## Test infrastructure
- `@SpringBootTest` + Testcontainers PostgreSQL (per-schema), WireMock for GEST/PAC/ACS/DASH/HIST, an SSE
  test client (`WebTestClient` streaming) for the live channel.
- No broker/Kafka test harness (messaging disabled) — domain events are asserted in-process.

## Coverage goals (EARS-derived)
- Every EARS `SHALL` in SPEC-01..07 maps to at least one scenario or step assertion.
- The four safety invariants (one-report-one-Evento, no-Evento-without-validation, emergency-always-
  dispatches, feedback-terminal-and-attributed) each have a dedicated scenario.
