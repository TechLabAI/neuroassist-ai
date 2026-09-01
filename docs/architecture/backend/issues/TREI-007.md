# TREI-007 -- NoAutoriaService — materializeNode + discardNode

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/9 itens

## Contexto

Materializa um nó rascunho a partir de uma proposta LLM (ou escrita manual): cria
`conv_no_arvore_fala` (estrutura locale-invariante) + `conv_no_conteudo(status='pendente',
confirmado_cuidadora=false, confirmado_paciente=false)` + `trei_conteudo_autoria
(sub_estado='rascunho')`. Invariante ADR-018: materializar o mesmo nó lógico em segundo
locale REUTILIZA o mesmo `noId` — apenas adiciona nova linha `(no_id, locale)`.
Discard: `sub_estado='descartado'` (soft), `conv_no_conteudo` fica `pendente` (CONV nunca serve).
`ativo` → 409 `already-published` no discard.

## Spec de referencia

- SPEC-01 (api-surface §2.3 POST /nos + §2.7 DELETE /nos/{noId})
- SPEC-04 (data-model §4 — publication state machine, rascunho branch)
- api-contract.md §2.3 (materialize) + §2.7 (discard — 409 on ativo)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/application/service/NoAutoriaService.java` — interface `materializeNode(MaterializeNoCommand) : NoAutoriaResult` + `discardNode(UUID noId, String locale, UUID cuidadoraId)`
- [ ] criar `src/main/java/com/neuroassist/trei/application/service/NoAutoriaServiceImpl.java` — implementação com `ConvTreeWriteAdapter` + `TreiConteudoAutoriaRepository` + `TreiPropostaLlmRepository`
- [ ] `materializeNode`: verificar se `noId` já existe para o `pacienteId` (reutilizar vs criar `conv_no_arvore_fala`); inserir `conv_no_conteudo(status=pendente, ambas flags false)` e `trei_conteudo_autoria(sub_estado=rascunho)`; atualizar `TreiPropostaLlm.estado=MATERIALIZADA` se `propostaId` fornecido
- [ ] invariante ADR-018: `tipo`, `parentNoId`, `engineFeatureRef`, `ordem` são locale-invariantes — stored apenas em `conv_no_arvore_fala`, não duplicados por locale
- [ ] `discardNode`: verificar `sub_estado` — se `ativo` lançar `AlreadyPublishedException` (409); caso contrário settar `sub_estado=DESCARTADO` (soft delete, ORG-001)
- [ ] criar `src/main/java/com/neuroassist/trei/domain/event/SpeechNodeProposed.java` — evento in-process para auditoria HIST
- [ ] criar `src/main/java/com/neuroassist/trei/domain/event/NodeDiscarded.java` — evento in-process para auditoria HIST
- [ ] auditar `SpeechNodeProposed` e `NodeDiscarded` via `HistAuditPort` (best-effort, capturar exceção)
- [ ] criar `src/test/java/com/neuroassist/trei/application/service/NoAutoriaServiceTest.java` — testar locale-invariance (segundo locale reutiliza noId), discard de ativo → 409, discard de rascunho → descartado

## Resultado esperado

Materialização cria estrutura locale-invariante; discard é soft e não apaga o nó (apenas
marca). CONV nunca serve nó pendente (conv_no_conteudo.status permanece pendente).

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*NoAutoriaServiceTest*"` sai 0
- [ ] Teste verifica que segundo locale para mesmo noId NÃO cria nova linha em `conv_no_arvore_fala`
- [ ] Teste verifica que discard de nó `ativo` lança `AlreadyPublishedException`
- [ ] Teste verifica que `conv_no_conteudo.status` permanece `pendente` após discard (não muda para `descartado`)

## Dependencias

- TREI-001 (entidades)
- TREI-003 (ports — HistAuditPort)
- TREI-009 (ConvTreeWriteAdapter — pode ser paralelizado com mocks no teste)

## Estimativa

L

## Prioridade

P2
