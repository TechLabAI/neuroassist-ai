# SPEC-09 — Messaging Architecture

> Service: `hist-service` · Module: HIST · Refs: ADR-005 · EARS notation (D-057).

## Objetivo

Registrar explicitamente a ausência de broker e o padrão REST-based que o substitui, mais o gatilho de
evolução para mensageria.

## Escopo

**In:** justificativa do modo síncrono, obrigação de durabilidade do produtor, gatilho de broker. **Out:**
qualquer implementação de fila/tópico (não existe no MVP).

## Decisões técnicas

- `stack.messaging.enabled = false` — **sem broker** no MVP (ADR-005). A ingestão de auditoria é síncrona
  via REST (`POST /audit-facts`), idempotente por `factId`.
- **Durabilidade** contra indisponibilidade transitória de HIST é responsabilidade do **outbox/retry do
  produtor** (mesmo padrão edge→backend). HIST contrata isso nas Specs dos produtores; não pode forçá-lo.
- **Broker-deferred**: se o volume/fan-out crescer (múltiplos consumidores, assinatura cross-service), a
  evolução natural é produtores publicarem eventos de domínio e HIST assinar. `FatoAuditado` seria o
  evento publicado natural (`event-contracts.md §3`). Gatilho: o mesmo que introduziria mensageria no
  projeto todo.

## Critérios de aceite (EARS)

- THE SYSTEM SHALL operate with no message broker and SHALL ingest audit facts synchronously over REST.
- THE SYSTEM SHALL rely on producer-side outbox/retry for durability and SHALL make replay safe via
  idempotency on `factId` (SPEC-05).
- THE SYSTEM SHALL neither publish nor consume any broker message in the MVP.
- WHERE a broker is later introduced, THE SYSTEM SHALL expose `FatoAuditado` as the published event and
  SHALL subscribe to producers' domain events instead of being a synchronous callee (deferred).
