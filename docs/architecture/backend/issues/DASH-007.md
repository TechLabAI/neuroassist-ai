# DASH-007 -- Implement GET /panel/summary: cross-module home view-model composition (EVNT + EDGE + ACS)

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

O home dashboard precisa de uma visao consolidada: lista de pacientes do cuidador autenticado
(escopo do ACS), ultimo evento/alarme por paciente (EVNT), liveness do dispositivo (EDGE)
e contagem de notificacoes nao-acknowledgeadas (EVNT). A composicao e otimista (fail-open
para EDGE e EVNT-read); apenas ACS e hard-dependency.

## Spec de referencia

- SPEC-01 (GET /panel/summary, scope from JWT, 200 SummaryView)
- SPEC-02 (composicao BFF: degradedSections, fail-open EDGE + EVNT)

## Escopo

- [ ] `application/PanelCompositionService.composeSummary(caregiverId, tenantId)`: chama IdentityDirectoryPort para resolver scope, paralleliza chamadas EVNT (latestEvent + unacknowledgedCount) e EDGE (device status) por paciente
- [ ] Degradacao: se EDGE indisponivel → `device.status = "unknown"`, `device.degraded = true`, adicionar "edge" a `degradedSections`; se EVNT indisponivel → omitir latestEvent + unacknowledgedCount, adicionar "evnt" a `degradedSections`
- [ ] `api/PanelController.getSummary()`: extrai caregiverId/tenantId do JWT, delega ao service, serializa `SummaryView` → `200`
- [ ] Teste de slice `@WebMvcTest` com mocks dos ports: scope resolvido, EDGE indisponivel → `degradedSections=["edge"]`, body valido

## Resultado esperado

`GET /panel/summary` retorna `SummaryView` consolidada dentro de 2s (upstream timeout configuravel).
Falhas de EDGE/EVNT sao representadas por `degradedSections`, nao por erro HTTP.

## Criterios de aceite

- [ ] Retorna `200` com `patients[]` restrito ao escopo do JWT
- [ ] EDGE indisponivel: `device.degraded = true`, `status = "unknown"`, `"edge"` em `degradedSections`
- [ ] EVNT indisponivel: `latestEvent = null`, `unacknowledgedCount` omitido, `"evnt"` em `degradedSections`
- [ ] ACS indisponivel: retorna `503 upstream-unavailable`
- [ ] JWT com `tenant` ausente ou invalido: retorna `401`

## Dependencias

- DASH-002 (ports definidos)
- DASH-003 (SummaryView e PatientSummary)
- DASH-016 (AcsRestAdapter)

## Estimativa

M

## Prioridade

P3
