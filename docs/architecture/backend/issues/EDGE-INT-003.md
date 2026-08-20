# EDGE-INT-003 -- Integration with GWY — upstream route /api/v1/edge/** + enrollment route without Bearer + X-Correlation-Id forwarding

**Status:** DONE
**Maturidade:** 85 -- Maduro
**Done/Total:** 4/4 itens

## Contexto
GWY e o ponto de entrada para todos os clientes externos (agente edge, DASH, PAC, GEST). A rota `/api/v1/edge/**` deve ser configurada no GWY com excecao para `POST /api/v1/edge/devices/enroll` (sem requisito de Bearer no GWY — o guard e o enrollment token). `X-Correlation-Id` deve ser gerado no GWY se ausente. Hub-integration §1.

## Spec de referencia
- SPEC-01, hub-integration.md

## Escopo
- [x] adicionar route definition no `neuroassist-ai-api-gateway`: predicado `Path=/api/v1/edge/**`, uri=`lb://edge-service`, filtros padrao de auth + forwarding
- [x] adicionar excecao de auth para `POST /api/v1/edge/devices/enroll` no filtro global de JWT do GWY (sem requisito de Bearer)
- [x] configurar `X-Correlation-Id` filter no GWY: gerar UUID se ausente, propagar para o upstream, ecoar na resposta
- [x] verificar que a rota `/api/v1/edge/**` descobre `edge-service` via REG (`lb://edge-service`)

## Resultado esperado
GWY roteia requests EDGE corretamente; POST /devices/enroll funciona sem Bearer; correlationId presente em todas as respostas.

## Criterios de aceite
- [ ] Request via GWY chega ao edge-service e retorna resposta correta
- [ ] POST /devices/enroll via GWY aceito sem Authorization header
- [ ] Response via GWY contem `X-Correlation-Id` mesmo quando request nao incluiu
- [ ] `lb://edge-service` resolve via REG Eureka (nao URL hardcoded)

## Dependencias
- EDGE-004 (precede)

## Estimativa
S

## Prioridade
P4
