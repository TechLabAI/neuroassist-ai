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

### §4.2 — Device Client Registration Strategy (EDGE-INT-002)

**Chosen approach: EDGE-brokered registration at enrollment time.**

When EDGE issues a credential during `POST /api/v1/edge/devices/enroll` (EDGE-006), it immediately
registers the corresponding `client_credentials` client in ACS so the device can obtain tokens.

| Step | Actor | Action |
|---|---|---|
| 1 | Operator | `POST /api/v1/edge/devices` — creates device + enrollment token (EDGE-005) |
| 2 | Edge agent | `POST /api/v1/edge/devices/enroll` — exchanges enrollment token for `clientId`/`clientSecret` |
| 3 | EDGE (sync, same tx) | Registers `clientId` in ACS via `POST /acs/admin/clients` with claims: `deviceId`, `tenant`, scope=`edge.device` |
| 4 | ACS | Persists the OAuth2 client; issues device tokens on `grant_type=client_credentials` |
| 5 | Edge agent | Uses `clientId`/`clientSecret` at ACS token endpoint → receives device access token |
| 6 | Edge agent | Sends heartbeats: `Authorization: Bearer <device_token>` |

**Rationale:** EDGE is the single authority over device identity. Centralising client registration
at enrollment time avoids race conditions and keeps ACS from having to validate unknown clients
against EDGE at token-issue time (the alternative approach).

**ACS admin endpoint contract (to be confirmed with ACS team):**

```http
POST /api/v1/acs/admin/clients
Authorization: Bearer <service-token with scope acs.admin>
Content-Type: application/json

{
  "clientId":    "<uuid>",
  "clientName":  "edge-device-<deviceId>",
  "grantTypes":  ["client_credentials"],
  "scopes":      ["edge.device"],
  "extraClaims": {
    "deviceId":  "<uuid>",
    "tenant_id": "<tenantId>"
  }
}
```

**Fallback during enrollment if ACS is unavailable:** enrollment fails with `503 acs-unavailable`
and the device must retry. The device record is NOT persisted until ACS registration succeeds
(transactional boundary). This prevents orphaned device records with unregistered clients.

**Feign client:** `AcsAdminClient` in `infrastructure.clients` (P5 stub; full implementation
deferred to EDGE-INT-002 when ACS publishes its admin API contract).

**Security note:** the service token used by EDGE to call ACS admin is obtained via
`client_credentials` with scope `acs.admin`. The `clientSecret` for this service client is
configured via `${ACS_SERVICE_SECRET}` (never hardcoded). The `AcsAdminClient` is conditionally
disabled in `application-test.yml` to avoid dependency on ACS in unit/integration tests.

**Tenant isolation guarantee:** per-device clients in ACS carry `tenant_id` as an extra claim.
EDGE validates that the `tenant_id` claim in the device token matches the `TenantContextHolder`
(set from the same JWT on every request). A mismatch triggers 403 `tenant_mismatch`.
