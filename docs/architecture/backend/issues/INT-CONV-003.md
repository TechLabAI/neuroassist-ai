# INT-CONV-003 -- Contrato CONV↔HIST — HistAuditPort REST (endpoint ingest, schema dos fatos SpeechNodeSelected/PhraseMatched/ConvServedDegraded)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto

O HIST (`hist-service`) deve expor um endpoint de ingestão de fatos de auditoria que o CONV
consome via `HistAuditPort`. Este contrato não foi especificado no lado do HIST ainda (HIST tem seu
próprio backlog). Esta issue garante que o contrato esteja alinhado entre os dois times antes do
scaffold, evitando que o CONV implemente `HistAuditFeignClient` com um path/schema que HIST não
reconhece.

## Spec de referencia

- SPEC-09 (messaging-architecture — auditoria síncrona, fatos)
- `contracts/event-contracts.md` §2 (shapes dos 4 fatos)
- `contracts/hub-integration.md` §4

## Escopo

- [ ] Confirmar com o backlog do HIST: (a) endpoint de ingest é `POST /hist/audit/ingest`? (b) schema do body aceita `tipo` discriminador + campos livres, ou schema typed por tipo de fato?
- [ ] Atualizar `contracts/hub-integration.md` §4 com: URL confirmada, request schema, response esperada (200/202), comportamento em duplicata (idempotência por `clientEventId` ou `correlationId` no HIST)
- [ ] Garantir alinhamento: `HistAuditFeignClient.java` (CONV-013) usa o endpoint confirmado; WireMock stub usa o mesmo schema
- [ ] Se HIST não tiver spec própria ainda, criar `contracts/integration-contracts.md` §HIST-stub com o schema proposto pelo CONV e marcá-lo como "pendente de validação com HIST backlog"

## Resultado esperado

Contrato CONV→HIST documentado e aceito pelo time de HIST; `HistAuditFeignClient` alinhado.

## Criterios de aceite

- [ ] `contracts/hub-integration.md` §4 contém endpoint, request schema e política de idempotência
- [ ] WireMock stub do HIST (CONV-013) usa o schema confirmado
- [ ] Se HIST ainda não tem spec: seção de stub documentada e marcada `[PENDENTE-HIST-SPEC]`

## Dependencias

- CONV-013 (HistAuditAdapter — afetado)

## Estimativa

S

## Prioridade

P4
