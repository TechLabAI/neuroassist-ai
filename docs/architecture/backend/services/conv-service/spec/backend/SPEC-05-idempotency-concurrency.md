# SPEC-05 — Idempotency and Concurrency

## Objetivo
Definir a idempotência das operações e a postura de concorrência de um serviço **read-mostly** cujo único
efeito colateral em runtime é o fato de auditoria.

## Escopo
**In:** idempotência de `POST /select` (chave `clientEventId`), retries seguros de `/search`,
dedupe no outbox; ausência de contenção write-write na árvore.
**Out:** política de retry para HIST (SPEC-09), concorrência de escrita do TREI (fora do CONV).

## Decisões técnicas
- `GET /tree`, `POST /search`, `POST /bindings/resolve` são idempotentes por natureza (leitura pura).
- `POST /select` não muta o estado da árvore; seu único efeito (fato HIST) é idempotente por
  `clientEventId` via `uq_conv_outbox_client_event` (data-model §2.4).
- **Sem contenção write-write** na árvore: CONV não escreve `conv_*` de conteúdo em runtime; leituras do
  CONV e escritas do TREI são desacopladas pela postura de projeção derivada (data-model §3).

## Implementação
- Cliente gera `clientEventId` (UUID) por seleção; reenvio com a mesma chave é no-op no outbox e emite o
  fato uma única vez.
- Insert no outbox com `ON CONFLICT (client_event_id) DO NOTHING` (índice unique parcial).
- Sem locks pessimistas; sem versionamento otimista necessário (nenhuma edição concorrente de árvore no
  runtime do CONV).

## Casos de borda
- Duplo clique / retry de rede na seleção → um único `SpeechNodeSelected`.
- `clientEventId` ausente → o fato é emitido, mas sem garantia de dedupe (cliente deveria sempre enviar).
- Reindexação/edição do TREI durante leitura → CONV lê o estado corrente; sem transação atômica
  PAC/TREI↔CONV (`ADR-013`, assumption A5).

## Critérios de aceite (EARS)
- WHEN two `POST /select` requests carry the same `clientEventId` within the idempotency window, THE SYSTEM SHALL emit exactly one `SpeechNodeSelected` fact.
- THE SYSTEM SHALL treat `GET /tree`, `POST /search`, and `POST /bindings/resolve` as safe to retry with no additional side effect.
- THE SYSTEM SHALL NOT hold write locks on tree/content rows at runtime, since it does not mutate them.
- IF `clientEventId` is absent on a selection, THEN THE SYSTEM SHALL still record the fact but MAY NOT guarantee dedupe.
