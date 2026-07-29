# SPEC-09 — Messaging Architecture (`edge-service`)

> **Module:** EDGE — Integração de Dispositivo Edge
> **ADR refs:** ADR-006 (no broker; liveness read-derived; DASH polls)
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Specify EDGE's messaging posture in a **broker-disabled** platform (`stack.messaging.enabled = false`):
in-process domain events only, REST for cross-service coordination, and a named extraction path for when
a broker is later introduced.

## 2. Scope

**In:**
- In-process (Spring `ApplicationEventPublisher`) domain events and their consumers
- The no-push consequence: DASH learns of offline by polling (ADR-006, doc 06 R1)
- Future-topic naming and the `DispositivoFicouOffline`-first extraction path

**Out:**
- Broker provisioning (none in MVP); the actual async wiring (deferred)

## 3. Posture (EARS)

- THE SYSTEM SHALL NOT publish to or consume from any message broker in the MVP — there is none.
- THE SYSTEM SHALL raise the events in `event-contracts.md` §2 **in-process** for local side effects
  (audit append, cache hint, metric), not as a public bus contract.
- THE SYSTEM SHALL raise domain events **post-commit**; IF a transaction rolls back, THEN THE SYSTEM SHALL
  NOT raise the event.
- THE SYSTEM SHALL expose liveness on a **pollable** endpoint (SPEC-02 §6) because it cannot push a
  "device offline" event without a broker.

## 4. Consumed events

- **None.** EDGE consumes no events. Cross-service inputs are synchronous REST calls: the edge agent's
  heartbeat (inbound) and the GEST profile pointer (outbound).

## 5. Published (in-process) events

See `events/event-catalog.md`. Anchor: `HeartbeatRecebido`. Safety-critical read-derived fact:
`DispositivoFicouOffline`.

## 6. Extraction path (deferred)

- THE SYSTEM SHALL name the future async topics with a `.v1` suffix (`edge.device.offline.v1`, …) so a
  broker rollout is additive with no rename.
- WHERE a broker is later enabled, `DispositivoFicouOffline` is the **first** event to move onto a topic,
  becoming the platform's proactive "monitoring dropped" alert — replacing DASH polling latency
  (doc 06 R1). This is explicitly out of MVP scope and revisited when patient volume grows
  (`stack.messaging` note).

## 7. Acceptance criteria

- No broker client/dependency is present in the MVP build.
- Every domain event is observed only after its transaction commits.
- DASH can determine offline solely by polling `GET /devices/{id}/liveness`; no push is required.
- Future-topic names are stable and documented so extraction requires no rename.
