# CONV-017 -- Criar OutboxRetrySweepService (@Scheduled, retry budget, pendente→enviado|descartado, idempotente por client_event_id)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

Sweep periódico que reenvia fatos de auditoria que falharam o envio síncrono inicial ao HIST.
`conv_audit_outbox` acumula linhas `status='PENDENTE'` quando o HIST está indisponível (CONV-006/013).
O sweep (`@Scheduled(fixedDelay = ...)`) processa na ordem de `created_at`, tenta reenviar via
`HistAuditAdapter`, marca `ENVIADO` no ACK ou incrementa `tentativas`. Após esgotar o budget de
tentativas (configurable, default 5), marca `DESCARTADO` e emite sinal operacional. Idempotente:
o índice único `uq_conv_outbox_client_event` garante que reprocessamento do mesmo `clientEventId`
pelo HIST seja idempotente no lado HIST.

## Spec de referencia

- SPEC-09 (messaging-architecture — sweep, retry budget)
- `contracts/data-model.md` §2.4 (AuditOutbox invariantes)

## Escopo

- [ ] Criar `application/service/OutboxRetrySweepService.java` com método `@Scheduled(fixedDelayString = "${neuroassist.conv.outbox.sweep-delay-ms:30000}") void sweep()`
- [ ] Lógica: carregar registros `status=PENDENTE` por `created_at ASC` (máx 50 por execução para não bloquear a thread scheduler); para cada um: chamar `HistAuditPort.send(fact)`; se sucesso → atualizar `status=ENVIADO`, `sentAt=now()`; se falha → `tentativas++`; se `tentativas >= maxTentativas` → `status=DESCARTADO` + log WARN com `noId` e `clientEventId`
- [ ] Budget configurável: `${neuroassist.conv.outbox.max-tentativas:5}`
- [ ] Habilitar scheduling: `@EnableScheduling` em `ConvServiceApplication` ou config separada
- [ ] Criar `infrastructure/persistence/adapter/AuditOutboxSweepAdapter.java` — wrapper transacional dos updates de status (`@Transactional`)
- [ ] Testes unitários: entry PENDENTE + HIST OK → ENVIADO; entry PENDENTE + HIST falha 3x → tentativas=3, PENDENTE; entry PENDENTE + HIST falha 5x → DESCARTADO + log emitido

## Resultado esperado

Sweep funcional com retry budget; `DESCARTADO` após esgotar tentativas; nenhum fato perdido silenciosamente.

## Criterios de aceite

- [ ] `./gradlew :conv-service:test` BUILD SUCCESS
- [ ] Teste unitário: 3 HIST falhas → `tentativas == 3`, status permanece `PENDENTE`
- [ ] Teste unitário: 5 HIST falhas → `status == DESCARTADO`, log WARN emitido (verificável com `LogCaptor`)
- [ ] Teste unitário: HIST OK na 2ª tentativa → `status == ENVIADO`, `sentAt` não nulo
- [ ] `@Scheduled` somente em `OutboxRetrySweepService` (nunca em um controller ou adapter — ArchUnit)
- [ ] `@Transactional` no adapter de update de status (não no service)

## Dependencias

- CONV-013 (HistAuditAdapter — chamado pelo sweep)
- CONV-009 (AuditOutbox repository)

## Estimativa

M

## Prioridade

P6
