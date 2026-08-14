# ADR-007 — Edge Device Authentication (INT-GEST-007)

**Status:** Accepted (MVP) — Under Review for production protocol  
**Date:** 2026-08-12  
**Deciders:** NeuroAssist AI Backend Team  
**Context:** INT-GEST-007 — transport authentication between gest-service and edge-service

---

## Context

`gest-service` calls `edge-service` via Feign to verify device pairing before serving a detection
profile (`GET /api/v1/edge/devices/{deviceId}/tenant`). The transport authentication mechanism
for service-to-service calls was left open in INT-GEST-007 pending a platform-wide decision by
the EDGE module team.

---

## Decision

**MVP choice:** OAuth2 `client_credentials` flow with scope `gest.profile.read`.

- `gest-service` authenticates itself to `edge-service` using a client credential obtained from
  Keycloak (`neuroassist-ai` realm).
- The `FeignClientConfig.feignAuthPropagationInterceptor` propagates the caller's JWT token to
  downstream services when available. For service-to-service calls without a user JWT, a dedicated
  client credentials token should be obtained via Spring Security's `OAuth2AuthorizedClientManager`.
- For the initial MVP, the user's JWT is propagated as-is (existing interceptor behaviour). This
  is acceptable when both services share the same Keycloak realm.

**Implementation isolation:** All transport authentication logic lives in `EdgePairingAdapter`
(`infrastructure.clients`). Changing the auth mechanism requires no changes to the controller,
domain, or application layers.

---

## Consequences

**Positive:**
- Simple to implement using existing Spring Security OAuth2 resource server configuration.
- Auth logic is fully isolated in the adapter — swap-safe for future protocol changes.
- Feign interceptors in `FeignClientConfig` handle header propagation without business-layer
  involvement.

**Negative / Risks:**
- Propagating user JWT to edge-service couples gest-service to the same Keycloak realm as
  edge-service. If edge-service moves to a different realm/IdP, this will break.
- The EDGE module team may require mTLS or a device-specific token in the future
  (INT-GEST-007 remains open for that decision).

---

## Temporary nature

This decision is explicitly provisional. When the EDGE team finalises the device authentication
protocol, this ADR should be superseded by a new ADR and `EdgePairingAdapter` updated accordingly.
No other files should require changes.

---

## References

- `INT-GEST-007` — Integration issue: transport auth for GEST → EDGE Feign calls
- `infrastructure/clients/EdgePairingAdapter.java` — isolation point
- `infrastructure/config/FeignClientConfig.java` — interceptors
