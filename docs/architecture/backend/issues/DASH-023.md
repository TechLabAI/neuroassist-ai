# DASH-023 -- Configure Actuator, structured logging, MDC X-Correlation-Id propagation and health indicators

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto

Observabilidade do `dash-service`: (1) `X-Correlation-Id` propagado de inbound request para MDC
e para todas as chamadas upstream (outbound headers); (2) logging estruturado (JSON) em prod;
(3) Actuator com `/actuator/health` e indicadores de upstream; (4) metricas de sessoes SSE
ativas (gauge).

## Spec de referencia

- SPEC-07 (observabilidade: Actuator, structured logging, X-Correlation-Id MDC, metricas SSE sessions)

## Escopo

- [ ] `CorrelationIdFilter implements OncePerRequestFilter`: extrai `X-Correlation-Id` do inbound header (ou gera UUID), adiciona ao MDC, propaga em todas as chamadas Feign (RequestInterceptor)
- [ ] Logback/JSON (prod profile): logger configurado com `%X{correlationId}` no padrao
- [ ] `management.endpoints.web.exposure.include=health,info,metrics`; `/actuator/health` exposto sem auth
- [ ] Gauge Micrometer: `dashboard.sse.sessions.active` = `PanelSessionRegistry.size()`
- [ ] Teste: request com `X-Correlation-Id: abc` → header `abc` presente em response e em todas as chamadas outbound (via WireMock request assertion)

## Resultado esperado

Toda request rastreavel por `X-Correlation-Id` no log e em todas as chamadas upstream; Actuator/health ativo.

## Criterios de aceite

- [ ] Request sem `X-Correlation-Id` → DASH gera UUID e ecoa no response header
- [ ] `X-Correlation-Id` propagado para todos os Feign clients como header outbound
- [ ] `correlationId` presente em todos os ProblemDetail responses
- [ ] `/actuator/health` retorna `200 {"status":"UP"}` sem autenticacao
- [ ] Gauge `dashboard.sse.sessions.active` incrementa/decrementa com sessoes SSE

## Dependencias

- DASH-001 (Spring Boot base)
- DASH-004 (PanelSessionRegistry: `size()`)

## Estimativa

S

## Prioridade

P7
