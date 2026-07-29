# EVNT — Event Catalog (`evnt-service`)

> Messaging is disabled (no broker). "Published" events are **in-process domain events** + their SSE
> projection; there is no bus consumer in the MVP. Full payloads: `contracts/event-contracts.md`.

## Domain events (in-process)

| Name | Type | Payload (ref) | Known consumers (in-process) |
|---|---|---|---|
| `EventoCriado` | published | event-contracts §2.1 | notification orchestrator, SSE fan-out (`evento.created`), HIST audit append |
| `NotificacaoDespachada` | published | event-contracts §2.2 | SSE fan-out (`evento.updated`), HIST audit append |
| `NotificacaoFalhou` | published | event-contracts §2.3 | retry sweep, HIST audit append, observability |
| `EventoConfirmado` | published | event-contracts §2.4 | SSE fan-out (`evento.updated`), HIST audit append |
| `FalsoPositivoMarcado` | published | event-contracts §2.5 | SSE fan-out, HIST audit append, false-positive-rate query (ADR-006) |
| `RecognizedCommandReport` | consumed (sync REST, not bus) | GEST detection contract §3.1 | EVNT ingress (`POST /reports`) |

## SSE frames (browser live channel)

| Frame `event:` | Projected from | Payload (ref) |
|---|---|---|
| `evento.created` | `EventoCriado` | event-contracts §4 |
| `evento.updated` | `EventoConfirmado` / `FalsoPositivoMarcado` / dispatch change | event-contracts §4 |
| `keepalive` | timer | `{serverTime}` |

## Broker-future note

Every domain event carries `eventVersion` and a JSON-serializable payload so it can be lifted onto async
topics **with no rename** WHERE a broker is introduced (Framing §11.2 growth trigger). Deferred, not
designed away (ADR-005).
