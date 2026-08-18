# SPEC-05 — Idempotency & Concurrency

## Objetivo
Garantir entrega/relato honestos e idempotentes, e comportamento correto sob concorrência de sessões e
redelivery, sem que o DASH detenha estado durável.

## Escopo
- **In:** idempotência de dispatch/callbacks por `dispatchId`; concorrência do registro de sessão; fail-open.
- **Out:** idempotência das escritas nos donos (PAC/EVNT owns; DASH não faz retry de write-through).

## Decisões técnicas
- **Chave de idempotência:** `dispatchId` (mintado pelo EVNT) para dispatch, `delivered` e `acknowledged`.
- **Janela:** vida do dispatch in-flight (até ack ou sessão sumir).
- **Write-through não é reexecutado** pelo DASH (evitar duplo-aplicar); apenas GETs de composição fazem 1 retry.
- **Registro de sessão** thread-safe; múltiplas conexões do mesmo cuidador são sessões distintas — dispatch
  vai para todas as sessões abertas do cuidador.

## Casos de borda
- Dois `POST /internal/notifications` com mesmo `dispatchId` → um único `delivered`.
- Ack repetido → `204` idempotente, sem segundo `acknowledged`.
- Cuidador com 2 abas abertas → notificação entregue em ambas; um único `delivered` (por dispatch).
- Sessão cai entre push e ack → dispatch permanece não-confirmado no EVNT; catch-up reentrega.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL key dispatch delivery and `delivered`/`acknowledged` callbacks by `dispatchId`.
- WHEN a `dispatchId` is redelivered, THE SYSTEM SHALL NOT emit a second `delivered` callback.
- WHEN an acknowledge is repeated, THE SYSTEM SHALL respond `204` idempotently without a second
  `acknowledged` callback.
- THE SYSTEM SHALL NOT retry caregiver write-throughs to PAC/EVNT.
- IF a target caregiver has no open session, THEN THE SYSTEM SHALL surface `no_open_session` so EVNT retry
  holds the emergency.
