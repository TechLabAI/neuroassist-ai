# SPEC-10 — Integrations

> Service: `evnt-service` · Synchronous REST integrations (no broker). Contracts:
> `contracts/hub-integration.md` (inter-module), `contracts/integration-contracts.md` (external).

## Objetivo
Specify the resilience contract of every integration EVNT depends on: timeouts, breakers, fallback posture,
and the fail-closed (emergency lookups) vs fail-open (notification) split.

## Escopo
- **In:** GEST, PAC, ACS, DASH, HIST Feign clients; edge ingress; resilience posture; the two open
  inter-module contracts (command-unresolvable, escalation) carried to Backlog.
- **Out:** the endpoint HTTP shapes (contracts files), auth (SPEC-06).

## Decisões técnicas — resilience posture
| Dependency | Direction | Timeout | Breaker | Failure posture |
|---|---|---|---|---|
| GEST validate | outbound | short (emergency-budgeted) | yes | **fail closed + surface** (`502/504`), no Evento |
| PAC `Comando` | outbound | short | yes | fail closed on emergency; deleted command → `422` |
| ACS caregiver | outbound | short | yes | fail closed on emergency (unroutable emergency escalated/logged) |
| DASH dispatch | outbound | short | yes | **fail open** → `queued/failed` + retry (never lose an emergency) |
| HIST append | outbound | short | yes | retryable off critical path (not a blocker) |
| edge → EVNT | inbound | — | — | edge retries via local outbox; EVNT idempotent |

- **Resilience4j** for timeouts/circuit breakers/retries; the notification retry is bounded + swept on boot.
- **Snapshotting** (ADR-004) means once an Evento exists it survives later dependency outages.

## Casos de borda / open inter-module contracts (→ Backlog)
- **Command-unresolvable** (PAC deleted/deactivated `Comando`) → `422 command-unresolvable`; contract with
  PAC to be confirmed (doc 06 assumption #1).
- **Escalation of an unacknowledged emergency** to a secondary caregiver depends on ACS routing — **open**
  (doc 06 gap #3), out of MVP; named for Backlog.
- **False-positive-rate consumer** (PAC sensitivity vs GEST thresholds) not yet contracted (doc 06 gap #5);
  EVNT only guarantees the signal is recorded and queryable.

## Critérios de aceite (EARS)
- WHILE processing an `emergencia`, IF GEST, PAC, or ACS does not respond within its timeout, THEN THE
  SYSTEM SHALL fail closed and surface the failure rather than create or route silently.
- WHEN a DASH dispatch fails, THE SYSTEM SHALL fail open, persist the dispatch as `queued`/`failed`, and
  retry it.
- IF a referenced `Comando` cannot be resolved via PAC, THEN THE SYSTEM SHALL reject ingress with `422`
  and create no Evento.
- THE SYSTEM SHALL treat a HIST append failure as retryable and non-blocking for the alarm path.
