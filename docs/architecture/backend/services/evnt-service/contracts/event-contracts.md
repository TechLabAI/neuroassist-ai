# EVNT — Event Contracts (`evnt-service`)

> Module: **EVNT — Eventos e Alarmes** · Service: `evnt-service`
> **Messaging is DISABLED** (`stack.messaging.enabled = false`, ADR-005 shared). There is **no broker,
> no topic, no queue** in the MVP. The "events" below are **in-process domain events**
> (`ApplicationEventPublisher`) plus the **SSE frames** EVNT pushes to the caregiver panel. They are named
> and shaped so they can be lifted onto async topics **with no rename** WHERE a broker is later introduced
> (Framing §11.2 growth trigger). This file is the versioned contract of those payloads.

## 1. Transport model (MVP)

| Concern | MVP transport | Deferred (broker) |
|---|---|---|
| Domain events (`EventoCriado`, …) | in-process `ApplicationEventPublisher`, same JVM/tx boundary | topic per event type |
| Live delivery to the caregiver panel | **SSE** `GET /events/stream` (+ `GET /events/active` poll fallback) | WS/STOMP or topic-fed push |
| Audit facts to HIST | synchronous REST (Feign), retryable | topic consumed by HIST |
| Ingress from edge | synchronous REST `POST /reports` (idempotent) | durable queue / outbox |

## 2. Published (in-process) domain events

> No external consumer subscribes over a bus in the MVP. In-process listeners drive: (a) the SSE fan-out,
> (b) the HIST audit append, (c) the notification dispatch. Payloads are JSON-serializable for the
> zero-rename broker future.

### 2.1 `EventoCriado`
Raised after a validated report is persisted as a new Evento (`feedback = nao_avaliado`).
```json
{
  "eventType": "EventoCriado", "eventVersion": 1,
  "eventoId": "e_44a1...", "tenant": "empresa_demo", "pacienteId": "b21e...",
  "comandoRef": "cmd_9a3f...", "commandLabelSnapshot": "Preciso de ajuda",
  "urgencia": "emergencia", "recognizedAt": "2026-07-28T14:03:10Z",
  "reportId": "8f1d5c1a-...", "seq": 10241, "correlationId": "..."
}
```
**Consumers (in-process):** notification orchestrator (dispatch), SSE fan-out (`evento.created` frame),
HIST audit append.

### 2.2 `NotificacaoDespachada`
Raised when a dispatch reaches `delivered`/`acknowledged`.
```json
{ "eventType": "NotificacaoDespachada", "eventVersion": 1, "eventoId": "e_44a1...", "dispatchId": "d_01...",
  "tier": "full_screen_alarm", "targetCaregiverId": "cg_...", "status": "delivered",
  "attempts": 1, "tenant": "empresa_demo", "correlationId": "..." }
```

### 2.3 `NotificacaoFalhou`
Raised when a dispatch attempt fails and is re-queued (retry pending) or exhausts its budget.
```json
{ "eventType": "NotificacaoFalhou", "eventVersion": 1, "eventoId": "e_44a1...", "dispatchId": "d_01...",
  "tier": "full_screen_alarm", "attempts": 3, "exhausted": false, "lastError": "dash 503",
  "tenant": "empresa_demo", "correlationId": "..." }
```

### 2.4 `EventoConfirmado`
Raised when a caregiver classifies the Evento `real`.
```json
{ "eventType": "EventoConfirmado", "eventVersion": 1, "eventoId": "e_44a1...", "verdict": "real",
  "confirmadoPor": "cg_...", "confirmadoAt": "2026-07-28T14:04:02Z", "tenant": "empresa_demo", "correlationId": "..." }
```

### 2.5 `FalsoPositivoMarcado`
Raised when a caregiver classifies the Evento `falso_positivo` (feeds false-positive rate for tuning).
```json
{ "eventType": "FalsoPositivoMarcado", "eventVersion": 1, "eventoId": "e_44a1...", "verdict": "falso_positivo",
  "comandoRef": "cmd_9a3f...", "confirmadoPor": "cg_...", "confirmadoAt": "2026-07-28T14:05:00Z",
  "tenant": "empresa_demo", "correlationId": "..." }
```

**EARS — publication**
- WHEN a new Evento is persisted, THE SYSTEM SHALL publish exactly one `EventoCriado` within the ingress
  transaction boundary before any dispatch attempt.
- WHEN a dispatch transitions to `failed` with retries remaining, THE SYSTEM SHALL publish
  `NotificacaoFalhou` with `exhausted=false` and re-queue the dispatch.
- WHEN a caregiver verdict is set, THE SYSTEM SHALL publish `EventoConfirmado` or `FalsoPositivoMarcado`
  exactly once (terminal transition, ADR-006).

## 3. Consumed events

**None over a bus.** EVNT's only *inbound async-shaped* input is the edge's `RecognizedCommandReport`,
delivered **synchronously** over REST (`POST /reports`, api-contract §3.1) — its schema is owned by the
**GEST detection contract §3.1**, not by EVNT. EVNT validates it against GEST before acting.

## 4. SSE frame contract (live channel)

The browser-facing manifestation of §2 domain events (api-contract §3.3). Frame `event:` names and `data`:

| `event:` | Source domain event | `data` |
|---|---|---|
| `evento.created` | `EventoCriado` | `{eventoId, urgencia, tier, commandLabel, recognizedAt, feedback}` |
| `evento.updated` | `EventoConfirmado` / `FalsoPositivoMarcado` / dispatch status change | `{eventoId, feedback, confirmadoPor?, confirmadoAt?, dispatchStatus?}` |
| `keepalive` | timer | `{serverTime}` |

Each frame carries `id: <evento.seq>` for `Last-Event-ID` resume.

**EARS — SSE**
- WHEN `EventoCriado` fires for an `emergencia`, THE SYSTEM SHALL emit an `evento.created` frame to every
  open stream in the event's tenant.
- WHEN a client sends `Last-Event-ID`, THE SYSTEM SHALL replay all frames with `seq` greater than it.

## 5. Idempotency

| Aspect | Rule |
|---|---|
| Idempotency key | `reportId` (client-generated UUID), unique per tenant schema. |
| Window | ≥ 24h (aligned with the GEST detection-contract idempotency window); pilot keeps indefinitely. |
| Guarantee | one report ⇒ one Evento ⇒ at-least-one dispatch; a duplicate returns the original verdict, no new event, no new dispatch. |
| Dispatch retries | bounded budget; each attempt increments `attempts`; a retried delivery is not a new Evento. |

**EARS — idempotency**
- IF a `reportId` is already in the idempotency ledger, THEN THE SYSTEM SHALL NOT publish a second
  `EventoCriado`.

## 6. Versioning

- Every domain-event and SSE payload carries `eventVersion` (starts at `1`). Additive fields do not bump it;
  a breaking change increments it and, in the broker future, forks the topic.
- The consumed `RecognizedCommandReport` version is pinned/validated by GEST (api-contract §7).

## 7. References

- ADR-005 (urgency-tiered notification, broker-deferred), ADR-004 (system of record), ADR-006 (feedback).
- `contracts/api-contract.md` §3.3 (SSE), `SPEC-09` (messaging architecture), `events/event-catalog.md`.
