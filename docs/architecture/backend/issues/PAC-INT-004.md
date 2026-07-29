# PAC-INT-004 -- Implementar HistFactClient Feign + outbox local de retry (HistUnavailableException, 2 retries, sem rollback)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
Adapter Feign para `HistFactPort`. PAC registra 3 domain facts em HIST: `ComandoPublicado`,
`ComandoRecalibrado`, `ComandoAposentado`. Após 2 retries automáticos (idempotent POST — HIST dedup
por `(comandoId, fact, calibracaoVersao, occurredAt)`), se HIST ainda indisponível, lança
`HistUnavailableException`. O lifecycle já commitou — **não** rolar back, só enfileirar no outbox
local para retry assíncrono (SPEC-10 §7.3).

## Spec de referencia
- SPEC-10 §7 (HistFactPort — interface, REST shape, resilience)
- `contracts/event-contracts.md` §1 (domain facts — payload e retry)

## Escopo
- [ ] implementar `HistFactClient` (`@FeignClient(name="hist-service")`):
      `POST /api/hist/v1/facts` com payload DomainFact; response `201` on success
- [ ] implementar `HistFactAdapter implements HistFactPort`:
      `record(DomainFact)`: chama Feign com 2 retries;
      em falha definitiva lança `HistUnavailableException`
- [ ] implementar `HistRetryOutbox` (in-memory `BlockingQueue<DomainFact>` + `@Scheduled` retry):
      PAC-015/016/018 chamam `HistFactPort.record` e no catch de `HistUnavailableException`
      invocam `HistRetryOutbox.enqueue(fact)`; o outbox reprocessa a cada 60s
- [ ] configurar timeout: `connectTimeout=2000, readTimeout=5000` para `hist-service`
- [ ] configurar payload com `calibracaoVersao` e `occurredAt` (SPEC-10 §7.2)
- [ ] TODO[agent]: itens adicionais de escopo

## Criterios de aceite
- [ ] HIST disponível: `record` completa sem enfileirar no outbox
- [ ] HIST indisponível após 2 retries: `HistUnavailableException` lançada; lifecycle NÃO revertido
- [ ] `ComandoPublicado` contém `calibracaoVersao` correto no payload
- [ ] Outbox processa enfileirados quando HIST volta online (retry agendado)
- [ ] Todos os calls propagam `X-Correlation-Id` e `X-Empresa-Id`
- [ ] TODO[agent]: criterio de aceite verificavel

## Dependencias
- PAC-003 (HistFactPort interface)
- INT-PAC-005 (FeignHeaderPropagator)

## Estimativa
M

## Prioridade
P6
