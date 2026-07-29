# SPEC-05 — Idempotency, concurrency & statelessness

> The gateway is stateless (INV-6); this SPEC covers correlation-based traceability,
> rate-limit concurrency, retry safety and the per-replica counter caveat (R-3). EARS per
> standard.

## Objetivo
Especificar o comportamento do gateway sob concorrência e restart, e sua contribuição para
idempotência downstream — sem manter estado durável.

## Escopo
- **In:** statelessness, correlationId para dedupe downstream, semântica de retry por
  método, concorrência dos buckets de rate-limit, comportamento em restart.
- **Out:** idempotência de negócio (responsabilidade dos serviços de domínio),
  distribuição global de contadores (Redis — fora do MVP).

## Decisões técnicas
- **Idempotência do gateway:** o gateway não deduplica; ele **propaga** `X-Correlation-Id`
  para que serviços downstream possam deduplicar requisições reenviadas pelo outbox do edge.
- **Retry:** apenas métodos idempotentes (`GET`) podem ser re-tentados uma vez contra outra
  instância descoberta; `POST`/mutações **nunca** são re-tentadas automaticamente.
- **Rate-limit concurrency:** buckets token-bucket thread-safe; in-memory por réplica no MVP
  (R-3 — limite efetivo = N× por N réplicas; documentado, aceito).
- **Restart:** perder buckets/cache JWK é aceitável; nenhuma requisição confirmada é perdida
  (não há estado durável).

## Implementação
- `X-Correlation-Id` estável por request; mesmo valor reenviado pelo edge reaparece nos
  logs downstream → chave natural de correlação/dedupe.
- Retry policy configurada por rota; default: `GET` retry=1 em connect-error/`503`.

## Casos de borda
- Edge reenvia o mesmo evento após 429/timeout com o mesmo `X-Correlation-Id` → downstream
  deduplica; o gateway apenas encaminha.
- Duas réplicas contam o mesmo principal separadamente → limite frouxo (R-3).

## Critérios de aceite (EARS)
- THE SYSTEM SHALL forward the same `X-Correlation-Id` on a retried request so downstream
  services can deduplicate.
- IF an upstream call for a non-idempotent method fails, THEN THE SYSTEM SHALL NOT retry it
  automatically.
- WHERE a `GET` upstream call fails with a connect error, THE SYSTEM SHALL retry at most
  once against another discovered instance before responding `503`.
- THE SYSTEM SHALL remain correct across restarts, losing only ephemeral rate-limit and JWK
  cache state (INV-6).
