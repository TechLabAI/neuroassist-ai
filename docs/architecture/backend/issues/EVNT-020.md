# EVNT-020 -- Configure Actuator health endpoints (liveness /health/liveness, readiness /health/readiness)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Exposes Kubernetes-compatible liveness and readiness probes via Spring Actuator. The `GET /health`
endpoint (api-contract §2.8) maps to Actuator's standard health groups. Liveness = is the process
alive (no deadlock). Readiness = is the service ready to serve (DB connection valid, Eureka registered).
Both endpoints are public (no auth, api-contract §2.8).

## Spec de referencia
- SPEC-07 (observability + health)
- contracts/api-contract.md §2.8

## Escopo
- [ ] Enable `management.endpoints.web.exposure.include=health,info,metrics` in `application.yml`
- [ ] Configure liveness group: `management.endpoint.health.group.liveness.include=livenessState`
- [ ] Configure readiness group: `management.endpoint.health.group.readiness.include=readinessState,db`
- [ ] Add custom `HealthIndicator` for SSE emitter registry size (warn if emitter count > threshold — configurable `evnt.sse.max-emitters`)
- [ ] Register `GET /health` path (Actuator `/actuator/health`) as allowed without auth in `SecurityFilterChain` (EVNT-018)
- [ ] Smoke test: `GET /actuator/health/liveness` → `{"status":"UP"}`; `GET /actuator/health/readiness` → `{"status":"UP"}` when DB is connected

## Resultado esperado
`/actuator/health/liveness` and `/actuator/health/readiness` return `200 UP` when the service is
healthy and `503 OUT_OF_SERVICE` when not, enabling Kubernetes to route traffic correctly.

## Criterios de aceite
- [ ] `GET /actuator/health/liveness` returns `200 {"status":"UP"}` without authentication
- [ ] `GET /actuator/health/readiness` returns `200 {"status":"UP"}` when PostgreSQL is reachable
- [ ] `GET /actuator/health/readiness` returns `503` when database connection fails (Testcontainers: stop container, poll)
- [ ] Actuator `/metrics` endpoint is accessible (for Prometheus scraping)

## Dependencias
- EVNT-001 (scaffold — Spring Boot Actuator dependency)
- EVNT-018 (SecurityFilterChain must permit `/actuator/health/**`)

## Estimativa
S

## Prioridade
P7
