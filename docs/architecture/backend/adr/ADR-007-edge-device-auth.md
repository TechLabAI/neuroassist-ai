# ADR-007 — Edge Device Authentication (INT-GEST-007)

**Status:** Accepted (final)  
**Date:** 2026-08-12 (decisão de transporte finalizada em 2026-08-26)  
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

---

## Finalização (2026-08-26)

Esta decisão deixa de ser provisória: **OAuth2 `client_credentials` é o protocolo definitivo** para
o transporte GEST→EDGE, não apenas uma escolha de MVP. Racional: o projeto ainda é pessoal/pré-produção
(poucos dispositivos, mesmo realm Keycloak controlado pelo próprio time), e mTLS adicionaria custo de
implementação (emissão/rotação de certificados por serviço, configuração adicional no Feign/Spring) sem
um requisito de segurança concreto que o justifique hoje.

**Gatilho de revisão** (não uma data, uma condição): reabrir esta decisão se e quando qualquer um destes
ocorrer — (a) `edge-service` passar a atender dispositivos de terceiros fora do controle direto do time,
(b) `edge-service` migrar para um realm/IdP diferente do de `gest-service`, ou (c) houver requisito
explícito de compliance/segurança que exija autenticação mútua por certificado. Até lá, não há ação
pendente.

Esta finalização cobre apenas o transporte serviço-a-serviço (GEST→EDGE). A autenticação de
**dispositivo da estação do paciente** (browser → backend, `clientSecret` no cliente) é um problema
diferente, tratado em `ADR-008-estacao-do-paciente-como-dispositivo.md` (frontend), e continua com
sua própria pendência (P1) sobre armazenamento seguro de credencial — não afetada por esta decisão.

---

## References

- `INT-GEST-007` — Integration issue: transport auth for GEST → EDGE Feign calls
- `infrastructure/clients/EdgePairingAdapter.java` — isolation point
- `infrastructure/config/FeignClientConfig.java` — interceptors
