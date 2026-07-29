# Setup (local) — GWY (API Gateway)

## HTTP port
- **Gateway:** `8080` (`server.port`) — the single public ingress locally.

## Runtime dependencies (must be up first)
| Dependency | Purpose | Local default |
|---|---|---|
| Service Registry (REG / Eureka) | route discovery; hard dependency | `http://localhost:8761/eureka` |
| ACS (`acs-service`) | JWK set / issuer for caregiver JWT validation | `http://localhost:<acs-port>` (via discovery) |
| At least one routed service (e.g. EVNT) | to exercise a real proxied route | via discovery |

> Postgres / Artemis are **not** dependencies of the gateway — it owns no schema and uses
> no broker.

## Environment variables
| Var | Meaning | Example |
|---|---|---|
| `SERVER_PORT` | gateway port | `8080` |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | Eureka endpoint | `http://localhost:8761/eureka` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | ACS JWK set | `http://localhost:8081/.well-known/jwks.json` |
| `GATEWAY_CORS_ALLOWED_ORIGINS` | dashboard origin(s) | `http://localhost:4200` |
| `GATEWAY_RATELIMIT_EDGE_INGEST_REPLENISH` | tokens/s for `edge-ingest` bucket | `50` |
| `GATEWAY_RATELIMIT_EDGE_INGEST_BURST` | burst capacity | `100` |
| `GATEWAY_RATELIMIT_LOGIN_REPLENISH` | tokens/s for `login` bucket | `5` |
| `GATEWAY_DEVICE_TOKEN_ISSUER` | expected issuer for device tokens (ADR-005) | `acs://edge` |

## Run
```bash
# 1. start Service Registry (REG) and ACS first (see their setup-local)
# 2. run the gateway
cd backend
./gradlew :neuroassist-ai-api-gateway:bootRun
```

## Smoke test
```bash
# liveness (public)
curl -s http://localhost:8080/actuator/health            # -> {"status":"UP"}

# unauthenticated routed request is rejected at the edge
curl -i http://localhost:8080/api/hist/events            # -> 401 application/problem+json

# correlation id is generated when absent (check response header)
curl -sD - -o /dev/null http://localhost:8080/actuator/health | grep -i x-correlation-id

# authenticated routed request (needs a caregiver JWT from ACS)
curl -i -H "Authorization: Bearer $JWT" http://localhost:8080/api/hist/events
```

## Notes
- The gateway is the only WebFlux (reactive/Netty) service in the backend — **no blocking
  calls in filters** (R-5).
- Rate-limit counters are in-memory per replica in local/MVP (R-3); a single local instance
  behaves as a strict limiter.
