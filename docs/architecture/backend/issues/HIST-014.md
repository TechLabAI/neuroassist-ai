# HIST-014 -- OAuth2 resource server: ACS JWK set local validation + scope-based authorization

## Contexto

`hist-service` é um OAuth2 resource server: valida JWTs ACS-emitidos localmente contra
o JWK set do ACS (sem callback por request). Scopes: `hist:write` (ingestão),
`hist:read` (cuidadora), `hist:read:all` (coordenador/DPO). Rejeitar ausência de token
com 401; scope incorreto com 403 (SPEC-06).

## Spec de referencia

- SPEC-06 (Security — OAuth2 resource server, scopes)
- `contracts/api-contract.md §6 Auth`

## Escopo

**`com.neuroassist.hist.infrastructure.security`:**
- `SecurityConfig` (`@Configuration @EnableWebSecurity`):
  - `POST /api/v1/hist/audit-facts` → requer scope `hist:write`.
  - `GET /api/v1/hist/audit-facts/**`, `GET /api/v1/hist/history/**`, `GET /api/v1/hist/fact-types` → requer `hist:read` ou `hist:read:all`.
  - `spring.security.oauth2.resourceserver.jwt.jwk-set-uri: ${neuroassist.acs.jwk-set-uri}` em `application.yml`.
  - JWK set cacheado localmente; refresh automático via Spring Security (no callback por request).
  - `JwtGrantedAuthoritiesConverter`: mapeia claim `scope`/`scp` → `SCOPE_hist:write`, `SCOPE_hist:read`, etc.
- `application-test.yml`: `spring.security.oauth2.resourceserver.jwt.jwk-set-uri: http://localhost:${wiremock.server.port}/jwks` (stub WireMock JWK).
- Testes com `@WithMockUser(roles=...)` ou JWT builder para validar autorização.

## Resultado esperado

Requisição sem token → 401; `hist:read` em POST /audit-facts → 403; `hist:write` em
POST /audit-facts → processado. JWT inválido (assinatura) → 401.

## Criterios de aceite

- [ ] `POST /api/v1/hist/audit-facts` sem bearer token → `401 Unauthorized`.
- [ ] `POST /api/v1/hist/audit-facts` com token `hist:read` (sem `hist:write`) → `403 Forbidden`.
- [ ] `POST /api/v1/hist/audit-facts` com token `hist:write` → `201` ou `200`.
- [ ] `GET /api/v1/hist/history` com token `hist:read` → `200`.
- [ ] JWT com assinatura inválida → `401`.
- [ ] JWK set URI configurado em `application.yml` (não hardcoded).
- [ ] Nenhuma chamada ACS no caminho de validação de token (local apenas).

## Dependencias

- HIST-001 (spring-boot-starter-oauth2-resource-server já na build)
- HIST-005, HIST-013 (endpoints a proteger)

## Estimativa

M

## Prioridade

P5
