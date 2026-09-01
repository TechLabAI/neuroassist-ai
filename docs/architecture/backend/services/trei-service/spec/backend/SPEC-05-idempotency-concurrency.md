# SPEC-05 — Idempotency + Concurrency

> EARS acceptance criteria (D-057). Related:
> [`../../contracts/data-model.md`](../../contracts/data-model.md) §2.4/§4,
> [`../../contracts/event-contracts.md`](../../contracts/event-contracts.md) §3.

## Objetivo

Garantir que aprovações, disparo do gate e passos do pipeline sejam idempotentes e livres de corrida —
sem broker, num pipeline in-process com outbox.

## Escopo

- **In:** idempotência de aprovação, disparo único do pipeline, idempotência de cada passo do outbox,
  concorrência das duas aprovações assíncronas.
- **Out:** idempotência de leitura do CONV (outro serviço).

## Decisões técnicas

- Aprovação é idempotente por estado do flag: setar `true` sobre `true` é no-op.
- Disparo do pipeline é idempotente por `UNIQUE (no_id, locale, passo)` em `trei_publicacao_outbox` — a
  transição de gate cria os 3 passos uma única vez; replay não duplica.
- Cada passo é idempotente: `embed` regrava o mesmo vetor/`_id` determinístico; `tts` reusa arquivo
  existente; `index` faz upsert em `_id = <no_id>_<locale>`.
- Concorrência das duas aprovações: escrita otimista no agregado `NoArvoreFala`; a 2ª aprovação relê os
  flags dentro da transação antes de avaliar o gate (evita duplo disparo em corrida cuidadora/paciente).

## Implementação

- Avaliação do gate e criação do outbox na mesma fronteira transacional; `INSERT ... ON CONFLICT DO
  NOTHING` nos passos do outbox (idempotência de criação).
- Drenador do outbox usa `proxima_em` (backoff) e `SELECT ... FOR UPDATE SKIP LOCKED` para não processar o
  mesmo passo em paralelo.

## Casos de borda

- Cuidadora e paciente aprovam quase simultaneamente → apenas um cria o outbox (ON CONFLICT).
- Reprocessar `tts` com arquivo já presente → nenhuma nova síntese ElevenLabs (custo, FIN-002).
- `index` reenviado após sucesso → upsert idempotente, sem efeito colateral.

## Critérios de aceite (EARS)

- WHEN two selections/approvals carry the same logical key, THE SYSTEM SHALL apply the effect at most once.
- WHEN the dual-authorization gate clears, THE SYSTEM SHALL create the three outbox steps exactly once even
  under duplicate/replayed approval calls.
- WHEN a `tts` step is retried and the cached file already exists, THE SYSTEM SHALL NOT request a new
  ElevenLabs synthesis.
- WHEN an `index` step is retried, THE SYSTEM SHALL upsert the same deterministic `_id = <no_id>_<locale>`
  with no duplicate document.
- WHILE the outbox drainer processes a step, THE SYSTEM SHALL prevent a second worker from processing the
  same `(no_id, locale, passo)` concurrently.
