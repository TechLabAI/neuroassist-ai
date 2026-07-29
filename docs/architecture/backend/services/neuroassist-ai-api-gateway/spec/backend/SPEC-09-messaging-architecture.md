# SPEC-09 — Messaging architecture (none)

> `stack.messaging.enabled = false` (A-4). The gateway uses no broker. This SPEC records
> that decision and the synchronous back-pressure substitute. EARS per standard.

## Objetivo
Documentar formalmente a ausência de mensageria no gateway e o mecanismo síncrono que a
substitui na borda (429 + outbox do edge).

## Escopo
- **In:** declaração de zero broker, contrato de back-pressure síncrona, caminho futuro.
- **Out:** qualquer tópico/fila/exchange; publicação/consumo de eventos.

## Decisões técnicas
- Todas as chamadas downstream são REST síncronas resolvidas por discovery (04-direction).
- **Back-pressure síncrona:** bucket de rate-limit esgotado → `429` + `Retry-After`; o
  outbox local do edge segura e re-tenta com backoff (substitui enfileiramento assíncrono
  no ingresso).
- Nenhuma dependência de client de broker no build do gateway.

## Implementação
- Nenhum `spring-*-stream`/`spring-*-amqp`/Kafka client presente.
- O contrato de 429/Retry-After é a interface de pacing para o edge (SPEC-05).

## Casos de borda
- Crescimento de volume do edge → introduzir broker **atrás** de um serviço de ingest; o
  gateway roteia para ele e continua sem publicar mensagens (futuro, fora do MVP).

## Critérios de aceite (EARS)
- THE SYSTEM SHALL NOT declare, publish to, or consume from any broker.
- WHEN a route's rate-limit bucket is exhausted, THE SYSTEM SHALL signal back-pressure with
  `429` and a `Retry-After` header instead of queuing the request.
- THE SYSTEM SHALL contain no broker client dependency in its build.
