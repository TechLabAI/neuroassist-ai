# SPEC-09 — Messaging Architecture

## Objetivo
Registrar que o registry **não participa de nenhum barramento de mensagens**.

## Status
**Not Applicable (recorded).** See `contracts/event-contracts.md` e
`events/event-catalog.md`. A plataforma não usa message broker no MVP
(`stack.messaging.enabled = false`), e mesmo que um broker seja introduzido para
eventos de negócio no futuro, REG permanece síncrono e não participante.

## Escopo
**In:** a decisão explícita de zero participação em messaging; toda relação de REG
com outros módulos é registro + discovery sobre HTTP.
**Out:** tópicos, consumidores, produtores, janelas de idempotência de evento —
nenhum existe.

## Decisões técnicas
- Doc 04 §"Async vs Sync Decisions": REG é síncrono apenas; discovery usa o
  protocolo REST do Eureka. Só timers internos de lease/eviction (schedules do
  servidor, não messaging de aplicação).
- `_brief.md` §7-8: relação com cada módulo é registro/discovery, não evento; sem
  domain-anchor event de negócio (REG tem eventos internos do Eureka — ver
  `03-initial-domain-framing.md` §Domain Events — mas nenhum sai para um broker).

## Critérios de aceite (EARS)
`THE SYSTEM SHALL NOT produce any broker event.`
`THE SYSTEM SHALL NOT consume any broker event.`
`THE SYSTEM SHALL relate to every other module exclusively through registration and discovery over HTTP.`
