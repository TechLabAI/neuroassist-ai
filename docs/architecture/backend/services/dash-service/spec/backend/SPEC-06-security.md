# SPEC-06 — Security

## Objetivo
Especificar autenticação, autorização por escopo do cuidador, e separação de superfícies (`/panel` vs
`/internal`), sem que o DASH seja um segundo dono de identidade (ACS owns).

## Escopo
- **In:** OAuth2 resource server (JWT ACS), resolução de escopo cuidador↔paciente, auth service-to-service,
  LGPD (sem dado biométrico).
- **Out:** emissão de token/JWK (ACS owns), RBAC de papéis (ACS owns).

## Decisões técnicas
- **JWT validado localmente** contra o JWK set do ACS (`DASH_OAUTH2_JWK_SET_URI`); sem chamada ao ACS por
  assinatura.
- **Escopo** (`{tenantId, patientIds}`) resolvido do ACS e cacheado com TTL curto (`SCOPE_CACHE_TTL_SECONDS`,
  30s).
- **Proxy confused-deputy-safe:** DASH não repassa o JWT do cuidador cru aos donos; autoriza com o escopo e
  chama os donos com identidade de serviço + parâmetros de escopo explícitos.
- **`/internal/**`** exige authority `SVC_EVNT`; nunca exposto ao browser.

## Casos de borda
- JWT sem claim `tenant` → `401`.
- `patientId` fora do escopo → `403` sem revelar existência.
- JWK set indisponível → validação usa cache; se sem cache válido → `401`/`503` conforme configuração.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL validate panel JWTs locally against ACS's JWK set.
- THE SYSTEM SHALL resolve caregiver↔patient scope from ACS and enforce it on every read and write.
- IF a caregiver requests a patient outside scope, THEN THE SYSTEM SHALL respond `403` without revealing
  existence.
- THE SYSTEM SHALL require `SVC_EVNT` authority for `/internal/**` and SHALL NOT expose it to the browser.
- THE SYSTEM SHALL NOT forward the raw caregiver JWT to owner services.
- THE SYSTEM SHALL NOT store or handle biometric/video/landmark data (LGPD hard rule).
