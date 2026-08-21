# hist-service — Local Setup

> Module: HIST · Service: `hist-service` · Package: `com.neuroassist.hist` · HTTP port: **8089**

## Dependencies

| Dependency | Purpose | Required |
|---|---|---|
| PostgreSQL 15+ | ledger store (`t_<empresa>` schemas) | yes |
| MIG (`neuroassist-ai-migrations`) | applies `V<n>__hist_*.sql`; provisions tenant schemas | yes (for schema) |
| REG (`neuroassist-ai-service-registry`) | Eureka discovery | yes |
| GWY (`neuroassist-ai-api-gateway`) | edge routing | for end-to-end |
| ACS (`acs-service`) | JWK set (token validation) + caregiver patient-scope | yes (JWK); scope only for caregiver reads |

No broker, no TechLab AI/hub, no biometric/edge integration.

## Environment variables

| Variable | Example | Purpose |
|---|---|---|
| `SERVER_PORT` | `8089` | HTTP port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/neuroassist` | DB |
| `SPRING_DATASOURCE_USERNAME` | `hist_service` | service role (INSERT/SELECT on ledger) |
| `SPRING_DATASOURCE_PASSWORD` | `***` | — |
| `NEUROASSIST_ACS_URL` | `http://localhost:8088` | ACS directory + JWK set |
| `NEUROASSIST_ACS_JWKSET_URI` | `http://localhost:8088/oauth2/jwks` | JWT validation |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | `http://localhost:8761/eureka` | REG |
| `NEUROASSIST_HIST_FACTTYPE_VERSION` | `v1` | active factType vocabulary version |

## Run & test

```bash
# 1. Ensure PostgreSQL is up and MIG has provisioned at least one tenant schema (t_<empresa>).
# 2. Ensure REG (8761) and ACS (8088) are running.

./gradlew :hist-service:bootRun

# health
curl -s http://localhost:8089/actuator/health | jq

# append an audit fact (needs a hist:write service token)
curl -s -X POST http://localhost:8089/api/v1/hist/audit-facts \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"factId":"018f-0001","factType":"evento.confirmado",
       "subject":{"type":"paciente","id":"pac-1"},
       "actor":{"caregiverId":"cg-1","displayNameSnapshot":"Ana"},
       "occurredAt":"2026-08-16T13:59:58Z","payload":{"verdict":"confirmed"}}'

# query timeline
curl -s "http://localhost:8089/api/v1/hist/history?subjectType=paciente&subjectId=pac-1" \
  -H "Authorization: Bearer $TOKEN" | jq

# verify chain
curl -s "http://localhost:8089/api/v1/hist/history/verify?subjectType=paciente&subjectId=pac-1" \
  -H "Authorization: Bearer $TOKEN" | jq
```

## Tests

```bash
./gradlew :hist-service:test                 # unit + slice
./gradlew :hist-service:cucumber             # BDD features in tests/cucumber/*.feature
```
