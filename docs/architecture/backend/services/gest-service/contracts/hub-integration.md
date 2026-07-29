# GEST — Hub Integration Contract (`gest-service`)

> Module: **GEST — Detecção de Gesto (Edge)** · Service: `gest-service`
> Hub services: GWY (neuroassist-api-gateway), REG (neuroassist-service-registry), MIG (neuroassist-migration-service)

## 1. GWY — API Gateway integration

`gest-service` is an upstream service registered in the Gateway.

| Aspect | Value |
|---|---|
| Logical service name | `gest-service` |
| Route prefix | `/api/v1/gest/**` |
| Discovery | Eureka (REG) |
| Auth delegation | GWY validates Bearer JWT; GEST re-validates token signature at service boundary |
| `X-Correlation-Id` | Generated at GWY if absent; forwarded on every proxied request |
| Rate limiting | Standard GWY policy (no GEST-specific override in MVP) |

- THE SYSTEM SHALL accept requests pre-validated by GWY but SHALL independently validate the
  JWT signature and `tenant` claim on every request.
- WHEN GWY forwards `X-Correlation-Id`, THE SYSTEM SHALL echo it in the response.

## 2. REG — Service Registry integration

- `gest-service` registers on startup as a Eureka client (`@EnableDiscoveryClient`).
- Health check URL: `http://<host>:<port>/actuator/health` (configured in Eureka metadata).
- The `EdgePairingClient` Feign client discovers `edge-service` via REG (no hardcoded URL).
- THE SYSTEM SHALL deregister from REG gracefully on shutdown (`eureka.client.fetchRegistry=true`,
  graceful shutdown enabled).

## 3. MIG — Migration Service integration

GEST contributes DDL scripts to the centralized MIG service. GEST does **not** run Flyway locally.

| Aspect | Value |
|---|---|
| Migration range | `V0400__gest_initial_schema.sql` … `V0499` (reserved) |
| Schema target | `empresa` (per-tenant); global ext block for `pgcrypto` |
| Generator | `python scripts/be-migration-generator.py --service gest-service` |
| Applied by | MIG (`neuroassist-migration-service`) as part of tenant provisioning |

- THE SYSTEM SHALL NOT start in an unresolved schema state; `SchemaRoutingDataSource` fails fast
  if the tenant schema is not present (MIG must run before first GEST request for a new tenant).
- WHERE MIG is not yet applied for a tenant, THE SYSTEM SHALL respond `503` on tenant-scoped
  requests (schema resolution failure) rather than executing cross-schema queries.

## 4. ACS — Authentication / Authorization (inbound)

GEST consumes tokens issued by ACS (AuthenticationService). No direct Feign call to ACS;
token validation is done via `jwk-set-uri` (Spring Security OAuth2 Resource Server config).

| Config property | Value |
|---|---|
| `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | `http://acs-service/oauth2/jwks` (resolved via REG) |
| Required claims | `tenant`, `sub`, `scope` |
