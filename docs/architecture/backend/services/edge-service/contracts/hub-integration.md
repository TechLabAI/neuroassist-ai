# EDGE — Hub Integration Contract (`edge-service`)

> Module: **EDGE — Integração de Dispositivo Edge** · Service: `edge-service`
> Hub services: GWY (neuroassist-ai-api-gateway), REG (neuroassist-ai-service-registry),
> MIG (neuroassist-ai-migrations), ACS (auth boundary).

## 1. GWY — API Gateway integration

`edge-service` is an upstream service registered in the Gateway. The **edge agent** reaches EDGE only via
GWY — it is never exposed directly (doc 02, doc 04).

| Aspect | Value |
|---|---|
| Logical service name | `edge-service` |
| Route prefix | `/api/v1/edge/**` |
| Discovery | Eureka (REG) |
| Auth delegation | GWY validates Bearer JWT; EDGE re-validates token signature at service boundary |
| `X-Correlation-Id` | Generated at GWY if absent; forwarded on every proxied request |
| Rate limiting | Standard GWY policy; consider a per-device heartbeat cap post-MVP (SPEC-07) |

- THE SYSTEM SHALL accept requests pre-validated by GWY but SHALL independently validate the JWT
  signature and `tenant`/`deviceId` claims on every request.
- WHEN GWY forwards `X-Correlation-Id`, THE SYSTEM SHALL echo it in the response.
- THE SYSTEM SHALL route `POST /api/v1/edge/devices/enroll` through GWY without a Bearer requirement — it
  is guarded by the single-use enrollment token (api-contract §2.2), not a JWT.

## 2. REG — Service Registry integration

- `edge-service` registers on startup as a Eureka client (`@EnableDiscoveryClient`).
- Health check URL: `http://<host>:<port>/actuator/health` (configured in Eureka metadata).
- The `DetectionProfileClient` Feign client discovers `gest-service` via REG (no hardcoded URL).
- THE SYSTEM SHALL deregister from REG gracefully on shutdown (`eureka.client.fetchRegistry=true`,
  graceful shutdown enabled).

## 3. MIG — Migration Service integration

EDGE contributes DDL scripts to the centralized MIG service. EDGE does **not** run Flyway locally.

| Aspect | Value |
|---|---|
| Migration range | `V0700__edge_initial_schema.sql` … `V0799` (reserved) |
| Schema target | `empresa` (per-tenant); global ext block for `pgcrypto` |
| Generator | `python scripts/be-migration-generator.py --service edge-service` |
| Applied by | MIG (`neuroassist-ai-migrations`) as part of tenant provisioning |

- THE SYSTEM SHALL NOT start in an unresolved schema state; `SchemaRoutingDataSource` fails fast if the
  tenant schema is not present (MIG must run before first EDGE request for a new tenant).
- WHERE MIG is not yet applied for a tenant, THE SYSTEM SHALL respond `503` on tenant-scoped requests
  (schema resolution failure) rather than executing cross-schema queries.

## 4. ACS — Authentication / Authorization (inbound + device token issuance)

EDGE consumes tokens issued by ACS. Token validation is via `jwk-set-uri` (Spring Security OAuth2
Resource Server). Additionally, **device credentials issued by EDGE are exercised at ACS's token
endpoint** (`client_credentials`, api-contract §6.3): the edge agent exchanges its `clientId`/secret for a
device access token that carries `deviceId` + `tenant` claims.

| Config property | Value |
|---|---|
| `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | `http://acs-service/oauth2/jwks` (resolved via REG) |
| Required human claims | `tenant`, `sub`, `scope` (`edge.read` / `edge.manage`) |
| Required device claims | `tenant`, `deviceId`, `scope` (`edge.device`) |
| Required service claims | `tenant`, `scope` (`edge.internal`) |
| Device token endpoint | `http://acs-service/oauth2/token` (`grant_type=client_credentials`) |

- THE SYSTEM SHALL derive `tenantId` and `deviceId` exclusively from token claims (never body/path).
- **Open coordination item (backlog):** the exact ACS registration of per-device `client_credentials`
  clients (dynamic client registration vs. EDGE-brokered) is an ACS/EDGE integration detail to confirm
  in `/be-architect-backlog`; this contract fixes the token *shape* and claims.
