# EVNT — Local Setup (`evnt-service`)

> Module: **EVNT — Eventos e Alarmes** · Service: `evnt-service` · Java 21 / Spring Boot 3.5.x

## HTTP port
- **`evnt-service`:** `8085` (dev). Routed via GWY; registered in Eureka (REG).

> Port allocation is a project-wide concern; `8085` is the EVNT convention for this pilot. Confirm against
> the monorepo port registry at scaffold time.

## Local dependencies
| Dependency | Why | Local |
|---|---|---|
| PostgreSQL 15+ | tenant schema `t_<empresa>` (evento, notification_dispatch, report_idempotency) | docker/localhost:5432 |
| Eureka (REG) | discovery | `neuroassist-ai-service-registry` |
| Spring Cloud Gateway (GWY) | routing | `neuroassist-ai-api-gateway` |
| MIG | applies EVNT migrations to tenant schemas | `neuroassist-ai-migrations` |
| GEST / PAC / ACS / DASH / HIST | synchronous REST dependencies | run or stub (WireMock) |

No broker (messaging disabled). No TechLab AI dependency.

## Environment variables
```
SPRING_PROFILES_ACTIVE=local
SERVER_PORT=8085
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/neuroassist
SPRING_DATASOURCE_USERNAME=neuroassist
SPRING_DATASOURCE_PASSWORD=neuroassist
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://localhost:8761/eureka
# downstream service base URLs (via GWY or direct in dev)
EVNT_GEST_BASEURL=http://localhost:8081
EVNT_PAC_BASEURL=http://localhost:8082
EVNT_ACS_BASEURL=http://localhost:8083
EVNT_DASH_BASEURL=http://localhost:8084
EVNT_HIST_BASEURL=http://localhost:8086
# live channel tuning
EVNT_SSE_KEEPALIVE_SECONDS=15
EVNT_DISPATCH_RETRY_MAX_ATTEMPTS=5
# security (interim device-token — final shape open with EDGE, SPEC-06)
EVNT_OAUTH2_JWK_SET_URI=http://localhost:8080/.well-known/jwks.json
```

## Run & test
```
# migrations applied by MIG (do not run Flyway standalone here)
./gradlew :services:evnt-service:bootRun
./gradlew :services:evnt-service:test          # unit + BDD (Cucumber) + Testcontainers
```

## Smoke
```
# ingress (interim device token)
curl -X POST http://localhost:8085/api/v1/reports -H 'Authorization: Bearer <device-jwt>' \
  -H 'Content-Type: application/json' -d @sample-report.json
# live stream
curl -N http://localhost:8085/api/v1/events/stream -H 'Authorization: Bearer <caregiver-jwt>'
# active (poll fallback / hydration)
curl http://localhost:8085/api/v1/events/active -H 'Authorization: Bearer <caregiver-jwt>'
```
