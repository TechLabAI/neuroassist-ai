# DASH-019 -- Implement HistRestAdapter (HistoryQueryPort): paged audit query forwarding to HIST

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto

O HIST fornece a timeline auditavel de eventos por paciente. O `HistRestAdapter` encaminha os
filtros (type, actor, from, to, page, size) ao HIST sem transformacao logica. HIST e hard-dependency
para a tela de historico (503 se indisponivel, sem degrade util).

## Spec de referencia

- SPEC-10 (`HistoryQueryPort`, upstream: `hist-service`, `HistRestAdapter`)

## Escopo

- [ ] `infrastructure/adapter/HistRestAdapter implements HistoryQueryPort` usando Feign (`HistClient`)
- [ ] `queryHistory(patientId, filters)`: `GET /patients/{patientId}/audit?type=&actor=&from=&to=&page=&size=` repassando `filters` inalterados; retorna `HistoryPage` mapeado da resposta do HIST
- [ ] HIST indisponivel → `UpstreamUnavailableException` (mapeado para 503)
- [ ] WireMock test: query com filtros, HIST down → excecao

## Resultado esperado

Timeline auditavel entregue diretamente do HIST ao caller sem transformacao dos filtros.

## Criterios de aceite

- [ ] Todos os filtros presentes na query string da chamada ao HIST (nenhum omitido silenciosamente)
- [ ] `size` ja foi capado a 100 pelo `PanelCompositionService` antes de chegar ao adapter (nao duplicar cap aqui)
- [ ] HIST down → `UpstreamUnavailableException`
- [ ] WireMock test passa

## Dependencias

- DASH-002 (HistoryQueryPort interface)
- DASH-003 (HistoryPage)

## Estimativa

S

## Prioridade

P5
