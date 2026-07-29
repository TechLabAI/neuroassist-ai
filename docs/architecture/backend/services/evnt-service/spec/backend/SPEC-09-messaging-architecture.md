# SPEC-09 — Messaging Architecture

> Service: `evnt-service` · **Messaging is DISABLED** (`stack.messaging.enabled = false`, ADR-005 shared).
> This SPEC documents the *deliberate absence* of a broker and the broker-ready design.

## Objetivo
Make explicit that EVNT has no message broker in the MVP, how "events" are realized without one, and the
zero-rename path to a broker WHERE growth triggers it.

## Escopo
- **In:** in-process domain events, the SSE live channel, synchronous REST couplings, the broker-future
  migration note.
- **Out:** any Kafka/RabbitMQ/Artemis configuration (none exists).

## Decisões técnicas
- **No broker, no topic, no queue.** Inter-module calls are synchronous REST (Feign) via GWY/REG.
- **Domain events are in-process** (`ApplicationEventPublisher`): `EventoCriado`, `NotificacaoDespachada`,
  `NotificacaoFalhou`, `EventoConfirmado`, `FalsoPositivoMarcado`. Listeners drive SSE fan-out, HIST append,
  and dispatch.
- **Durability without a broker:** the Evento is persisted first (ADR-004); dispatch status is persisted
  (`notification_dispatch`) so an in-process retry + boot-time sweep replaces broker redelivery.
- **Live delivery is SSE**, not a message bus (SPEC-03).
- **Broker-ready:** every domain-event payload is JSON-serializable and carries `eventVersion`; names are
  frozen so they lift onto topics **with no rename** WHERE a broker is introduced (Framing §11.2 growth
  trigger: patient/edge count grows beyond pilot).

## Casos de borda
- evnt-service restart mid-retry → boot sweep re-queues persisted `queued`/`failed` dispatches (the
  broker-provided durability substitute).

## Critérios de aceite (EARS)
- THE SYSTEM SHALL NOT depend on any message broker, topic, or queue in the MVP.
- THE SYSTEM SHALL realize inter-module communication as synchronous REST and browser delivery as SSE.
- WHEN evnt-service starts, THE SYSTEM SHALL sweep persisted `queued`/`failed` dispatches and re-attempt
  them, substituting for broker redelivery.
- THE SYSTEM SHALL keep every domain-event name and payload broker-compatible (`eventVersion`, JSON) so a
  future broker adoption requires no rename.
