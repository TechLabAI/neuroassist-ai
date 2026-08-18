# SPEC-03 — Notification Delivery over SSE

## Objetivo
Especificar a entrega em tempo real das notificações do EVNT ao painel do cuidador via **SSE**, o catch-up
on-connect e os callbacks `delivered`/`acknowledged` (ADR-005). Este é o motivo de existir do DASH: a última
ponta humana do pedido de ajuda do paciente.

## Escopo
- **In:** `/panel/stream` (SSE), registro de sessão in-memory, sink `/internal/notifications`, catch-up,
  callbacks de entrega/ack, fallback long-poll.
- **Out:** origem/urgência da notificação (EVNT owns), política `urgencia → tier` (EVNT ADR-005).

## Decisões técnicas
- **SSE** (`SseEmitter`) com heartbeat a cada `HEARTBEAT_SECONDS` (default 15s).
- **Registro de sessão in-memory** (`SseSessionRegistry`) — nunca persistido; truth é a conexão viva.
- **Fail-open:** cuidador sem sessão aberta → `no_open_session` reportado ao EVNT (que faz retry); DASH
  nunca absorve/perde um dispatch.
- **Idempotência** por `dispatchId` (ver SPEC-05).

## Implementação
- `DeliverNotificationUseCase`: recebe dispatch (EVNT), procura sessão do `targetCaregiverId`, empurra evento
  SSE `notification`, reporta `delivered`.
- `ReplayUnacknowledgedUseCase`: no connect, puxa não-confirmadas do EVNT, replaya, emite `catchup-complete`.
- Tier **sempre** vem do EVNT no payload; DASH nunca decide urgência.

## Casos de borda
- Reconexão após queda → catch-up traz não-confirmadas; nenhuma perdida.
- Redelivery do mesmo `dispatchId` → sem segundo `delivered`.
- Restart do DASH → todas as sessões caem; painéis reconectam e disparam catch-up.

## Critérios de aceite (EARS)
- WHEN a `NotificationDispatch` arrives and the target caregiver has an open session, THE SYSTEM SHALL push it
  over SSE and report `delivered(dispatchId)` to EVNT.
- IF the target caregiver has no open session, THEN THE SYSTEM SHALL report `no_open_session` and emit no
  `delivered` signal.
- WHEN a panel reconnects, THE SYSTEM SHALL replay unacknowledged notifications from EVNT and emit
  `catchup-complete`.
- THE SYSTEM SHALL emit a `heartbeat` every `HEARTBEAT_SECONDS` while a session is open.
- THE SYSTEM SHALL take `tier` from EVNT's dispatch and SHALL NOT decide urgency itself.
- THE SYSTEM SHALL never fabricate a `delivered` signal to silence EVNT retry.
