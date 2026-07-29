# EVNT-019 -- Implement device-token validation filter (interim JWT-shaped token: deviceId, tenant, paciente claims; SPEC-06 interim contract)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
The `POST /reports` endpoint accepts a device-scoped token. The final shape (mTLS vs OAuth2
`client_credentials`) is an open item tracked in INT-EVNT-001 (gap #1 from M2 handoff). This issue
implements the **interim contract** defined in SPEC-06: a JWT-shaped device token with claims
`deviceId`, `tenant`, `paciente`, `exp`. The filter validates the signature (JWK Set URI), extracts
`deviceId`, `tenant`, `pacienteId` claims, and verifies `tenant` matches the patient's tenant. Mismatch
→ `403`. This interim implementation must be swappable with mTLS or OAuth2 `client_credentials` once
INT-EVNT-001 is resolved.

## Spec de referencia
- SPEC-06 (security — device-token interim contract)
- INT-EVNT-001 (open item: mTLS vs OAuth2 client_credentials)

## Escopo
- [ ] Create `DeviceTokenAuthFilter` (extends `OncePerRequestFilter`) that intercepts only `POST /api/v1/reports`
- [ ] Validate `Authorization: Bearer <device-token>` — verify JWT signature via shared JWK Set URI (same `EVNT_OAUTH2_JWK_SET_URI` or separate device JWK endpoint)
- [ ] Extract `deviceId`, `tenant`, `paciente` claims; reject missing/malformed with `401`
- [ ] Verify `tenant` from token matches `pacienteId`'s tenant (call `TenantContextHolder` + cross-check); reject with `403` on mismatch — do not reveal cross-tenant information
- [ ] Populate `TenantContextHolder.setTenant(token.tenant)` after successful validation
- [ ] Implement an `DeviceAuthenticationToken` (Spring Security `Authentication` object) carrying `deviceId` and `tenant` for downstream use
- [ ] Mark filter as swappable: isolate device-token logic behind `DeviceTokenValidator` interface so the implementation can be replaced (mTLS or OAuth2) without touching the filter

## Resultado esperado
`POST /reports` validates the interim device JWT, rejects malformed/expired/mismatched tokens, and
populates tenant context for the downstream ingress service.

## Criterios de aceite
- [ ] Valid device JWT → filter passes, `TenantContextHolder` populated with `token.tenant`
- [ ] Missing or malformed JWT → `401 /errors/unauthenticated`
- [ ] JWT `tenant` != patient's tenant → `403 /errors/tenant-mismatch`
- [ ] `DeviceTokenValidator` interface is the sole entry point for validation logic — implementation is swappable
- [ ] Caregiver endpoints are unaffected by this filter (device filter applies only to `/api/v1/reports`)

## Dependencias
- EVNT-018 (SecurityFilterChain — device filter is registered there)
- INT-EVNT-001 (must be resolved before final hardening; this issue implements interim)

## Estimativa
M

## Prioridade
P6
