# PAC-INT-006 -- Criar WireMock stubs para GEST/ACS/EDGE/HIST nos testes de integração + Cucumber runner

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto
Criar os WireMock stubs que simulam GEST, ACS, EDGE e HIST nos testes de integração e Cucumber.
Os stubs devem cobrir os happy paths e os cenários de falha específicos documentados nos
`.feature` files de M2.5 (ex.: GEST retornando `supported=false` para o emergency gate,
GEST timeout para fail-closed). Sem os stubs, os step definitions (PAC-025) não podem passar.

## Spec de referencia
- SPEC-08 (BDD test scenarios — feature file scenarios)
- SPEC-03 §5.3 (GEST result interpretation table)
- `tests/cucumber/emergency-eligibility-gate.feature`

## Escopo
- [ ] implementar `GestWireMockStub`: stubs para:
      `GET /api/gest/v1/tipos-gesto/{id}` → `200` com `{id, nome, estado}` (happy path)
      `GET /api/gest/v1/tipos-gesto/{unknown-id}` → `404`
      `GET /api/gest/v1/tipos-gesto/{id}/detectability` → `{supported: true}` (supported)
      `GET /api/gest/v1/tipos-gesto/{pending-id}/detectability` → `{supported: false}` (pending-training)
      timeout stub (delay > 2s) para cenário fail-closed
- [ ] implementar `AcsWireMockStub`: `GET /api/acs/v1/empresas/{id}` e `/cuidadores/{id}` → `200`/`404`
- [ ] implementar `EdgeWireMockStub`: `GET /api/edge/v1/dispositivos/{id}` → `200`/`404`
- [ ] implementar `HistWireMockStub`: `POST /api/hist/v1/facts` → `201` (happy), `503` (failure test)
- [ ] configurar `application-test.yml` com URLs apontando para WireMock em vez de Eureka
- [ ] TODO[agent]: itens adicionais de escopo

## Criterios de aceite
- [ ] `GestWireMockStub.pendingTraining(tipoGestoId)` faz o emergency gate retornar `422`
- [ ] `GestWireMockStub.timeout(tipoGestoId)` faz o emergency gate retornar `503`
- [ ] Testes Cucumber passam com todos os serviços mockados (sem chamadas reais a GEST/ACS/EDGE/HIST)
- [ ] `HistWireMockStub.unavailable()` verifica que lifecycle não é revertido mesmo com HIST down
- [ ] TODO[agent]: criterio de aceite verificavel

## Dependencias
- INT-PAC-001, INT-PAC-002, INT-PAC-003, INT-PAC-004 (adapters que os stubs simulam)
- PAC-025 (CucumberRunnerIT — usa estes stubs)

## Estimativa
M

## Prioridade
P8
