# SPEC-05 — Idempotency & Concurrency

> Service: `hist-service` · Module: HIST · Refs: ADR-005, ADR-006 · EARS notation (D-057).

## Objetivo

Garantir exactly-once por `factId` sob retry e ordenação gap-free por stream sob append concorrente.

## Escopo

**In:** idempotência por `factId`, serialização do tip do stream, resolução de corridas, janela de
idempotência. **Out:** broker (não existe, SPEC-09).

## Decisões técnicas

- **Idempotência**: `UNIQUE (fact_id)` + `audit_idempotency` como fonte de verdade de "já visto". O append
  consulta idempotência antes de encadear; uma violação de unicidade em corrida é convertida em resposta
  `200` com o registro original.
- **Concorrência de stream**: o tip é serializado por `SELECT stream_head … FOR UPDATE` (ou, sem
  `stream_head`, por `UNIQUE (stream_key, sequence_no)` + retry). Dois appends concorrentes no mesmo stream
  nunca produzem `sequenceNo` duplicado nem buraco.
- **Isolamento**: transação única cobre INSERT do record + INSERT idempotency + UPSERT stream_head.

## Casos de borda

- Retry do mesmo `factId` após sucesso → `200`, sem novo write, sem avanço de sequência.
- Dois `factId` diferentes no mesmo stream simultâneos → serializados; ambos gravam com `sequenceNo`
  consecutivos.
- Mesmo `factId` submetido por dois nós ao mesmo tempo → um grava, o outro pega violação de unicidade e
  responde idempotente.
- Falha após INSERT do record mas antes do commit → rollback total (nada persiste; producer re-tenta).

## Critérios de aceite (EARS)

- WHEN the same `factId` is submitted more than once (any concurrency), THE SYSTEM SHALL persist at most
  one `AuditRecord` and SHALL return the original on every subsequent submission.
- WHILE two appends target the same `streamKey` concurrently, THE SYSTEM SHALL assign strictly consecutive
  `sequenceNo` values with no gap and no duplicate.
- IF a unique-constraint violation on `(fact_id)` occurs during a race, THEN THE SYSTEM SHALL convert it to
  an idempotent `200` response with the existing record.
- IF a unique-constraint violation on `(stream_key, sequence_no)` occurs during a race, THEN THE SYSTEM
  SHALL re-read the stream tip and retry the append.
- THE SYSTEM SHALL commit the record insert, the idempotency entry and the stream-head update in a single
  transaction, or roll back all of them.
- THE SYSTEM SHALL retain the idempotency entry for at least 24h (pilot: indefinitely).
