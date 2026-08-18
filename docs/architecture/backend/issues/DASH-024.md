# DASH-024 -- Implement Cucumber BDD feature tests for home-status-calm, notification-delivery and calibration write-through

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto

Os tres cenarios BDD do DASH cobrem os fluxos de aceite principais: (1) home em estado calm
(nenhum alarme ativo, device online, summary 200); (2) entrega de notificacao SSE (dispatch
EVNT → push panel, ack cuidador); (3) write-through de calibracao (PAC aceita e rejeita).
Os feature files estao em `spec/backend/tests/` e foram gerados na fase de specs.

## Spec de referencia

- SPEC-08 (BDD: home-status-calm.feature, notification-delivery.feature, calibration-writethrough.feature)

## Escopo

- [ ] Glue code Cucumber para `home-status-calm.feature`: WireMock stubs ACS + EVNT + EDGE; GET /panel/summary → 200 com `degradedSections=[]`
- [ ] Glue code para `notification-delivery.feature`: POST /internal/notifications com sessao SSE aberta → push verificado + callback `delivered` ao EVNT (WireMock); POST .../acknowledge → callback `acknowledged`
- [ ] Glue code para `calibration-writethrough.feature`: PUT /panel/.../calibration com PAC aceitando → 200; PAC rejeitando → relay 422
- [ ] `@SpringBootTest` com WireMock para todos os upstreams; sem banco real (no datasource)

## Resultado esperado

Suite Cucumber passa para os tres features com upstreams mockados via WireMock.

## Criterios de aceite

- [ ] `./gradlew :dash-service:test` com perfil Cucumber executa sem falhas
- [ ] Todos os `Given/When/Then` dos tres features mapeados para step definitions
- [ ] WireMock verifica que callbacks EVNT foram chamados (delivery + ack)
- [ ] Cenario de rejeicao PAC: `422` com ProblemDetail relayado (sem modificacao)

## Dependencias

- DASH-005 ao DASH-015 (todos os endpoints e services)
- DASH-016 ao DASH-020 (adapters com WireMock)

## Estimativa

L

## Prioridade

P8
