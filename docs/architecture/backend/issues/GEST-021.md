# GEST-021 -- Expose Actuator health/readiness/liveness endpoints and Micrometer metrics (profile.versions.published, primitive.transitions.count)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
`gest-service` deve expor endpoints de observabilidade via Spring Boot Actuator: `/actuator/health` (liveness + readiness), `/actuator/prometheus` (metricas Micrometer), e `/actuator/info`. Duas metricas de dominio sao relevantes: `gest.profile.versions.published` (contador por tenant) e `gest.primitive.transitions.count` (contador por tipo de transicao). O `TenantSchemaHealthIndicator` (GEST-014) ja contribui para o health check.

## Spec de referencia
- SPEC-07, api-contract.md §6.1 (health endpoint publico)

## Escopo
- [ ] habilitar `management.endpoints.web.exposure.include=health,info,prometheus` em `application.yml`
- [ ] configurar `management.endpoint.health.show-details=when-authorized` para detalhes apenas autenticados
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/metrics/GestMetrics.java` (bean com `Counter gest.profile.versions.published` e `Counter gest.primitive.transitions.count` usando Micrometer `MeterRegistry`)
- [ ] injetar `GestMetrics` em `DetectionProfileService.publish()` e em `TipoGestoService.transition()` para incrementar contadores
- [ ] testes: `GET /actuator/health` retorna 200 sem autenticacao; `GET /actuator/prometheus` retorna metricas GEST apos operacoes

## Resultado esperado
`/actuator/health` retorna `{ "status": "UP" }` sem autenticacao; `/actuator/prometheus` expoe contadores GEST incrementados corretamente.

## Criterios de aceite
- [ ] `GET /actuator/health` sem JWT retorna `200 {"status": "UP"}` (endpoint publico)
- [ ] `GET /actuator/prometheus` retorna linhas com `gest_profile_versions_published_total` e `gest_primitive_transitions_count_total`
- [ ] contadores incrementam apos chamadas de publish() e transition() (verificado em teste de integracao)
- [ ] `GET /actuator/info` retorna nome do servico e versao

## Dependencias
GEST-001, GEST-009, GEST-007.

## Estimativa
S

## Prioridade
P7
