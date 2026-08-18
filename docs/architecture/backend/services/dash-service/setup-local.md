# DASH — Local Setup

> Service: `dash-service` · Port: **8090** (dev) · Java 21 / Spring Boot 3.5.x
> Routed via GWY; registered in Eureka (REG). Owns **no** database in the MVP.

## Ports (project map)

| Service | Port |
|---|---|
| `neuroassist-ai-api-gateway` (GWY) | 8080 |
| `pac-service` | 8082 |
| `gest-service` | 8084 |
| `evnt-service` | 8085 |
| `edge-service` | 8087 |
| `acs-service` | 8088 |
| `hist-service` | 8089 |
| **`dash-service`** | **8090** |
| `neuroassist-ai-migrations` (MIG) | 8093 |
| `neuroassist-ai-service-registry` (REG) | 8761 |

## Local dependencies

- **No PostgreSQL required** — DASH owns no tables (MVP). (Only needed if the optional, deferred
  `caregiver_ui_preference` table is ever built — not in MVP.)
- **No broker** — messaging disabled.
- **REG (Eureka)** running at `:8761` for discovery.
- **Owner services reachable** (via GWY or direct in dev): ACS `:8088`, PAC `:8082`, EVNT `:8085`,
  HIST `:8089`, EDGE `:8087`. In dev, unavailable upstreams simply exercise the degradation paths.

## Environment variables

```bash
SERVER_PORT=8090
EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://localhost:8761/eureka/

# OAuth2 resource server — validate panel JWTs against ACS JWK set
DASH_OAUTH2_JWK_SET_URI=http://localhost:8088/.well-known/jwks.json

# Upstream base URLs (via GWY or direct in dev)
DASH_ACS_BASE_URL=http://localhost:8088
DASH_PAC_BASE_URL=http://localhost:8082
DASH_EVNT_BASE_URL=http://localhost:8085
DASH_HIST_BASE_URL=http://localhost:8089
DASH_EDGE_BASE_URL=http://localhost:8087

# Tuning
UPSTREAM_TIMEOUT_MS=2000
SCOPE_CACHE_TTL_SECONDS=30
EDGE_STALENESS_SECONDS=45
HEARTBEAT_SECONDS=15
```

## Run & test

```bash
# from backend/ (Gradle root)
./gradlew :dash-service:bootRun

# health
curl -s http://localhost:8090/actuator/health

# open the SSE stream (requires a caregiver JWT)
curl -N -H "Authorization: Bearer $JWT" http://localhost:8090/panel/stream

# unit + slice tests
./gradlew :dash-service:test
```
