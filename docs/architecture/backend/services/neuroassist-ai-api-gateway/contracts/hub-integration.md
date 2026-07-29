# Hub / Platform Integration — GWY (API Gateway)

> **TechLab AI: none** (`ai.hasAi = false`). The gateway makes no orchestration /
> knowledge / document-composition / analytics calls. This document instead records the
> **platform-service** integrations the gateway depends on (Service Registry, ACS identity)
> and the **trust contract** downstream services rely on.

## 1. TechLab AI capabilities

| Capability | Used? |
|---|---|
| orchestration | no |
| knowledge | no |
| document_composition | no |
| analytics | no |

EARS:
- THE SYSTEM SHALL NOT invoke any TechLab AI capability.

## 2. Service Registry (REG / Eureka)

- **Role:** the gateway is a Eureka **client**. It registers itself and consumes the
  registry to resolve `lb://<service>` routes. Hard startup dependency for routing.
- **Endpoint:** `eureka.client.service-url.defaultZone` → REG (`neuroassist-ai-service-registry`).

EARS:
- THE SYSTEM SHALL register itself with the Service Registry on startup.
- WHILE the Service Registry is unreachable and the local instance cache is empty, THE
  SYSTEM SHALL report readiness `DOWN` and reject routed requests with `503`.
- THE SYSTEM SHALL resolve every proxied target via a logical service id, never a
  hard-coded host:port (INV-4).

## 3. Cuidadores e Acesso (ACS) — identity authority

- **Role:** issuer/authority of caregiver identity; exposes the JWK set / OIDC discovery
  the gateway uses to validate caregiver JWTs. With EDGE, the authority for device tokens
  (ADR-005, proposta).
- **Endpoints consumed:** ACS well-known JWK set (`spring.security.oauth2.resourceserver
  .jwt.jwk-set-uri`) and/or issuer (`issuer-uri`). No business ACS logic is embedded.

EARS:
- THE SYSTEM SHALL validate caregiver JWTs against ACS's JWK set and reject tokens with an
  invalid signature, wrong issuer/audience, or expiry as `401`.
- WHEN ACS rotates its keys (new `kid`), THE SYSTEM SHALL refresh the JWK set and validate
  new tokens without a redeploy.
- WHERE the JWK endpoint is temporarily unreachable but a cached key is still valid, THE
  SYSTEM SHALL continue validating with the cached key (stale-while-revalidate, R-4).

## 4. Downstream trust contract (services that trust the gateway)

Consumers: **EVNT, PAC, ACS, HIST, DASH, EDGE**. These services trust the gateway-supplied
context *because the gateway is the only public ingress* (ADR-005 shared trust; INV-2).

| Header supplied by gateway | Downstream expectation |
|---|---|
| `X-Empresa-Id` | authoritative tenant; select schema-per-empresa without re-deriving |
| `X-Principal-Kind` / `X-Principal-Id` | subject identity for fine-grained authz/audit |
| `X-Correlation-Id` | propagate on their own logs and any onward hop |

EARS:
- THE SYSTEM SHALL guarantee that a downstream service never receives a caller-supplied
  `X-Empresa-Id` (it is always stripped and re-set from the principal).
- THE SYSTEM SHALL forward `X-Correlation-Id` unchanged to the downstream service so the
  end-to-end trace is continuous.
