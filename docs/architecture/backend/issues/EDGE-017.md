# EDGE-017 -- Expose Actuator health/readiness/liveness endpoints and Micrometer metrics (heartbeats.received, pairing.transitions, profile.drift)

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
Observabilidade operacional: endpoints de health para Eureka e probes Kubernetes, e metricas de negocio para alertas (heartbeats por minuto, transicoes de pareamento, drift de perfil detectado). A health check URL e registrada no Eureka metadata.

## Spec de referencia
- SPEC-07

## Escopo
- [ ] configurar `management.endpoints.web.exposure.include: health,info,metrics,prometheus` no `application.yml`; incluir `management.endpoint.health.show-details=always`
- [ ] implementar `infrastructure/metrics/EdgeMetrics.java` com `MeterRegistry`: contador `edge.heartbeats.received` (tags: tenantId, status), contador `edge.pairing.transitions` (tags: fromState, toState), contador `edge.profile.drift.detected`
- [ ] injetar `EdgeMetrics` nos services (HeartbeatService, PairingService) e incrementar em cada operacao relevante
- [ ] adicionar `info.app.version=${project.version}` e `info.app.module=EDGE` no actuator info

## Resultado esperado
`GET /actuator/health` retorna UP; `GET /actuator/metrics/edge.heartbeats.received` retorna contador incrementado.

## Criterios de aceite
- [ ] `GET /actuator/health` retorna `{status:UP}` com sub-checks de db
- [ ] `GET /actuator/metrics/edge.heartbeats.received` disponivel apos primeiro heartbeat
- [ ] Contador `edge.pairing.transitions` incrementado a cada pair/unpair/retire
- [ ] Contador `edge.profile.drift.detected` incrementado quando drift detectado no heartbeat

## Dependencias
- EDGE-008 (precede)

## Estimativa
S

## Prioridade
P7
