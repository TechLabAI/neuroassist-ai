# SPEC-09 — Messaging Architecture (disabled) + Synchronous Audit + Outbox

## Objetivo
Registrar que o CONV **não usa broker** (`messaging.enabled = false`) e especificar como os fatos de
auditoria são entregues ao HIST de forma síncrona, best-effort e não-bloqueante, com um outbox local para
retry.

## Escopo
**In:** ausência de broker; envio síncrono ao HIST; `conv_audit_outbox`; sweep de retry; orçamento de
tentativas.
**Out:** conteúdo dos fatos (event-contracts); persistência da trilha (HIST).

## Decisões técnicas
- **Sem Kafka/RabbitMQ no MVP.** Todas as chamadas cross-service são REST síncronas (doc 04). Broker é
  decisão **futura** (reavaliar se escalar de 1 paciente para tenants institucionais).
- Auditoria ao HIST é **best-effort/não-bloqueante**: falha não pode impedir a comunicação do paciente.
- Outbox local (`conv_audit_outbox`) é a **única** escrita de runtime do CONV; dedupe por `client_event_id`.

## Implementação
- `HistAuditAdapter`: tenta `POST` síncrono; em falha, insere no outbox (`status: pendente`).
- Sweep agendado (`@Scheduled`) reenvia `pendente` em ordem de `created_at`; `pendente → enviado` no ACK;
  `pendente → descartado` após esgotar o orçamento de tentativas (`tentativas`), com log/sinal.
- Sem `@EmbeddedKafka` no contexto de teste (não há broker).

## Casos de borda
- HIST indisponível na seleção → `200` ao paciente, fato no outbox.
- Reprocessamento do sweep com o mesmo `client_event_id` → idempotente (unique parcial).
- Orçamento esgotado → `descartado` + sinal operacional (não bloqueia nada).

## Critérios de aceite (EARS)
- THE SYSTEM SHALL NOT depend on any async broker; all inter-service communication SHALL be synchronous REST.
- WHEN an audit send to HIST fails, THE SYSTEM SHALL persist the fact to `conv_audit_outbox` and SHALL still return success to the patient.
- THE SYSTEM SHALL retry buffered facts and mark them `enviado` on acknowledgment, `descartado` after the retry budget is exhausted.
- THE SYSTEM SHALL dedupe buffered facts by `client_event_id`.
