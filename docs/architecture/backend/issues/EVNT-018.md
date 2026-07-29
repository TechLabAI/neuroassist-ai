# EVNT-018 -- Configure OAuth2 resource server + scope enforcement (evnt:read, evnt:confirm on caregiver endpoints)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Configures Spring Security as an OAuth2 resource server for all caregiver-facing endpoints
(`/events/**`, `/events/stream`, `POST /events/{id}/confirmation`, `POST /events/{id}/dispatches/{id}/ack`).
Token validation is against the configured JWK Set URI (`EVNT_OAUTH2_JWK_SET_URI`). Scopes `evnt:read`
(all GET + stream) and `evnt:confirm` (confirmation + ack) are enforced via `@PreAuthorize` or
`SecurityFilterChain`. The `POST /reports` ingress path is handled separately by EVNT-019.

## Spec de referencia
- SPEC-06 (security — caregiver auth)

## Escopo
- [ ] Add `spring-boot-starter-oauth2-resource-server` dependency to `evnt-service`
- [ ] Configure `SecurityFilterChain` in `EvntSecurityConfig`: `POST /api/v1/reports` → custom device-token filter (EVNT-019); all `GET /api/v1/events/**` → require `evnt:read`; `POST /api/v1/events/**/confirmation` and `.../ack` → require `evnt:confirm`; `GET /health/**` → permit all (no auth)
- [ ] Configure `JwtDecoder` bean pointing to `${evnt.oauth2.jwk-set-uri}` (env: `EVNT_OAUTH2_JWK_SET_URI`)
- [ ] Enforce tenant isolation: add a `JwtAuthenticationConverter` that extracts `tenant` claim from JWT and sets `TenantContextHolder.setTenant(tenant)` after token validation
- [ ] Configure `@EnableMethodSecurity` to support `@PreAuthorize("hasAuthority('SCOPE_evnt:confirm')")` on confirmation service methods
- [ ] Security test: missing JWT on caregiver endpoint → 401; wrong scope on confirmation → 403; tenant mismatch → 403 (resource existence not leaked)

## Resultado esperado
All caregiver endpoints reject unauthenticated or under-scoped requests; the JWT `tenant` claim
populates `TenantContextHolder` after every validated caregiver request.

## Criterios de aceite
- [ ] `GET /api/v1/events/active` without valid JWT returns `401`
- [ ] `POST /api/v1/events/{id}/confirmation` with JWT lacking `evnt:confirm` scope returns `403`
- [ ] JWT with `tenant=empresa_a` cannot access events from `tenant=empresa_b` (403, not 404)
- [ ] `GET /actuator/health/liveness` is accessible without auth
- [ ] `POST /api/v1/reports` path is excluded from OAuth2 resource server filter (handled by device-token filter in EVNT-019)

## Dependencias
- EVNT-001 (scaffold)
- EVNT-012 (TenantContextHolder — populated here from JWT)
- INT-EVNT-001 (device-token shape — must be frozen before hardening this filter; interim uses mock validator)

## Estimativa
M

## Prioridade
P6
