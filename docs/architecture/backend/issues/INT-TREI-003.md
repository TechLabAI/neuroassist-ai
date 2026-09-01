# INT-TREI-003 -- INT: TREI -> HIST audit (5 facts, no clinical text, HIST outage buffered)

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

TREI audita 5 tipos de fato para HIST (event-contracts §4): `proposal.created`,
`approval.caregiver`, `approval.patient`, `node.published`, `node.discarded`. A
chamada é síncrona (REST via Eureka), mas HIST outage é bufferizada: exceção capturada
→ fato entra em outbox local para retry. Nenhum payload carrega texto clínico/diagnóstico
ou transcripts brutos (apenas referências por id, SEC-002).

## Spec de referencia

- event-contracts.md §4 (audit facts para HIST — 5 tipos, by reference)
- hub-integration.md §3 (HistAuditPort — synchronous, buffered on outage)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/hist/HistAuditAdapter.java` — implementa `HistAuditPort`; Feign client para `hist-service`; envia `AuditFact` com apenas ids e `correlationId` (sem texto clínico)
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/hist/HistOutboxDrainer.java` — `@Scheduled`; retry de fatos que falharam no envio inicial; mesmo mecanismo do `TreiPublicacaoOutbox` mas para HIST
- [ ] os 5 tipos de `AuditFact` como sealed classes / enums: `ProposalCreatedFact`, `ApprovalCaregiverFact`, `ApprovalPatientFact`, `NodePublishedFact`, `NodeDiscardedFact`
- [ ] verificar que nenhum campo de payload carrega `texto` ou `transcript` (apenas `sessaoId`, `propostaId`, `noId`, `locale`, `cuidadoraId/pacienteSessaoSub`, `correlationId`, `at`, `embeddingModelId`)
- [ ] criar `src/test/java/com/neuroassist/trei/integration/HistIntegrationTest.java` — WireMock de HIST: envio bem-sucedido; HIST down → fato vai para outbox local sem bloquear a operação principal

## Resultado esperado

Todos os 5 fatos auditados; HIST indisponível não bloqueia autoria; nenhum clinical
text no payload.

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*HistIntegrationTest*"` sai 0
- [ ] Teste verifica que `HistAuditPort` lançando exceção não propaga para o caller da operação principal (caught, buffered)
- [ ] Campos de payload auditados NÃO contêm `textoProposto` nem transcrições LLM (inspecionar `AuditFact` classes)

## Dependencias

- TREI-003 (HistAuditPort interface)
- TREI-006 (AutorizacaoService — dispara audit de aprovação)
- TREI-016 (PublicacaoService — dispara audit de publicação)

## Estimativa

M

## Prioridade

P4
