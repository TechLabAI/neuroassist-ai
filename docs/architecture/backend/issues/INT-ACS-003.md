# INT-ACS-003 -- HistAuditAdapter: Feign client para HIST (domain facts best-effort + retry outbox)

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto

`HistAuditAdapter` registra os 6 domain facts ACS no HIST. Best-effort: HIST unavailable
não bloqueia nenhuma operação do ACS. Retry outbox garante entrega eventual.

## Spec de referência

- `contracts/event-contracts.md §1` (6 domain facts, payload envelope)
- SPEC-10 §3.3 (HistAuditPort, best-effort, retry outbox)

## Escopo

- [ ] criar `HistAuditClient extends HistAuditPort` com `@FeignClient(name="hist-service")`
- [ ] configurar timeout 1s; falha → log WARN + enfileirar no retry outbox local (tabela `audit_outbox` no schema global)
- [ ] criar `AuditOutboxTable` (global): `(id, fact, payload jsonb, attempted_at, status)`
- [ ] criar `AuditOutboxRetryScheduler` (`@Scheduled`): reprocessa entradas `status=pending` com back-off exponencial (1m, 5m, 30m)
- [ ] propagar `correlationId` e `empresaId` em cada fact

## Critérios de aceite

- [ ] HIST unreachable → fact enfileirado no outbox, operação ACS não interrompida
- [ ] Outbox scheduler reenvia fact pendente quando HIST volta ao ar
- [ ] `AutenticacaoRealizada` não bloqueia response de login (best-effort assíncrono)

## Dependências

- ACS-001 (Feign)
- ACS-008 (migrations globais — incluir `audit_outbox` no V0200)
- HIST M5 scaffold

## Estimativa

M

## Prioridade

P4
