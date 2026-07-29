# Setup (local) — REG (Service Registry)

## HTTP port
- **Service Registry:** `8761` (standard Eureka port).

Eureka dashboard at `/`. Actuator (separate from the Eureka protocol surface):
`/actuator/health`, `/actuator/health/readiness`, `/actuator/prometheus`.

## Runtime dependencies (must be up first)
| Dependency | Purpose | Local |
|---|---|---|
| _(none)_ | REG is the dependency target; it calls no one | — |

REG is the **first** service to start locally — every other service (`api-gateway`,
`migrations`, `gest-service`, `evnt-service`, `pac-service`, `acs-service`,
`hist-service`, `dash-service`, `edge-service`) registers with it. No PostgreSQL,
no message broker, no JWKS for this service.

## Environment variables
| Var | Meaning | Example |
|---|---|---|
| `SERVER_PORT` | registry port | `8761` |
| `EUREKA_INSTANCE_HOSTNAME` | hostname advertised | `localhost` |
| `EUREKA_CLIENT_REGISTER_WITH_EUREKA` | single-node self-flag (ADR-002) | `false` |
| `EUREKA_CLIENT_FETCH_REGISTRY` | single-node self-flag (ADR-002) | `false` |
| `EUREKA_SERVER_ENABLE_SELF_PRESERVATION` | self-preservation toggle | `true` |
| `EUREKA_SERVER_EVICTION_INTERVAL_TIMER_IN_MS` | eviction loop period | `60000` |
| `EUREKA_SERVER_RENEWAL_PERCENT_THRESHOLD` | self-preservation threshold | `0.85` |

## Run
```bash
# registry is the first thing up — no dependencies
cd backend
./gradlew :neuroassist-ai-service-registry:bootRun
```

## Smoke test
```bash
# liveness / readiness
curl -s http://localhost:8761/actuator/health/readiness   # -> {"status":"UP"}

# dashboard (browser): http://localhost:8761/

# registry snapshot (JSON)
curl -s http://localhost:8761/eureka/apps -H "Accept: application/json"

# once another service is up with eureka.client.serviceUrl.defaultZone=http://localhost:8761/eureka/
# it appears in the snapshot above and on the dashboard
```

## Tests
```bash
./gradlew :neuroassist-ai-service-registry:test       # bootstrap/config tests
./gradlew :neuroassist-ai-service-registry:cucumber   # BDD (tests/cucumber/*.feature)
```
