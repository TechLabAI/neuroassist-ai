# DASH-009 -- Implement GET /panel/patients/{patientId}/history: paged read-through to HIST (size cap 100)

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto

A tela de historico exibe a timeline auditavel de eventos do paciente, fornecida pelo HIST.
O DASH valida o escopo, cap `size` a 100, repassa os filtros ao HIST sem transformacao e
retorna a pagina de resultados. HIST e hard-dependency para esta tela (503 se indisponivel).

## Spec de referencia

- SPEC-01 (GET /panel/patients/{patientId}/history, query: type, actor, from, to, page, size; 200 HistoryPage; 503 HIST down)
- SPEC-02 (composicao: HIST = fonte unica; sem degrade util)

## Escopo

- [ ] `application/PanelCompositionService.queryHistory(caregiverId, patientId, filters)`: valida escopo, aplica cap `size=min(requested, 100)`, chama HistoryQueryPort com filtros inalterados
- [ ] HIST indisponivel → `503 upstream-unavailable`
- [ ] `api/PanelController.getPatientHistory(patientId, @RequestParam ...)`: extrai e valida parametros, delega ao service, retorna `HistoryPage`

## Resultado esperado

`GET /panel/patients/{id}/history` encaminha a query ao HIST apos validacao de escopo e cap de `size`.

## Criterios de aceite

- [ ] `size > 100` e silenciosamente truncado para `100` antes da chamada ao HIST
- [ ] Filtros `type`, `actor`, `from`, `to`, `page`, `size` sao repassados ao HIST sem transformacao
- [ ] `patientId` fora do escopo retorna `403`
- [ ] HIST indisponivel → `503` com ProblemDetail `upstream-unavailable`

## Dependencias

- DASH-002 (HistoryQueryPort)
- DASH-003 (HistoryPage)
- DASH-016 (AcsRestAdapter: scope)
- DASH-019 (HistRestAdapter)

## Estimativa

S

## Prioridade

P3
