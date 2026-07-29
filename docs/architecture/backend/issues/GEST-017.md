# GEST-017 -- Configure OAuth2 JWT resource server (jwk-set-uri via REG, scope enforcement: gest.read / gest.manage / device credential)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
`gest-service` e um OAuth2 resource server. Tokens de cuidador carregam scopes `gest.read` e/ou `gest.manage`; tokens de dispositivo edge carregam `deviceId` e `tenant` claims (formato ainda aberto — GEST-025 formaliza). A `jwk-set-uri` aponta para `acs-service` descoberto via REG (sem URL hardcoded). Endpoints de health/actuator sao publicos. O `SecurityConfig` mapeia cada endpoint ao scope minimo requerido do api-contract.md §6.

## Spec de referencia
- SPEC-06, api-contract.md §6, hub-integration.md §4

## Escopo
- [ ] implementar `backend/services/gest-service/src/main/java/com/neuroassist/gest/infrastructure/config/SecurityConfig.java` (Spring Security `@Configuration`, `SecurityFilterChain` com JWT resource server, mapeamento de scopes por endpoint)
- [ ] configurar `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` como `${ACS_JWK_URI:http://acs-service/oauth2/jwks}` em `application.yml` (resolve via REG em producao, variavel de ambiente em testes)
- [ ] mapear: `GET /tipos-gesto/**`, `GET /detection-profiles/**`, `GET /detection-contract`, `POST /eligibility:check` → `gest.read`; `POST /tipos-gesto`, `POST /tipos-gesto/*/transitions`, `POST /detection-profiles` → `gest.manage`; `GET /devices/*/detection-profile`, `POST /recognized-commands` → device credential (claim `deviceId`)
- [ ] implementar extrator de `tenantId` do JWT claim `tenant` (populado em `TenantContextHolder`)
- [ ] testes de seguranca: token sem scope → 403; token expirado → 401; endpoint publico sem token → 200

## Resultado esperado
Todos os endpoints requerem JWT valido com scope correto; health/actuator sao publicos; tenantId extraido do JWT nunca do request body.

## Criterios de aceite
- [ ] `GET /tipos-gesto` sem token retorna `401 Unauthorized`
- [ ] `POST /tipos-gesto` com token de scope `gest.read` (sem `gest.manage`) retorna `403 /errors/insufficient-scope`
- [ ] `GET /actuator/health` sem token retorna `200` (endpoint publico)
- [ ] JWT expirado em qualquer endpoint retorna `401 Unauthorized`
- [ ] `tenantId` populado no `TenantContextHolder` vem exclusivamente do claim `tenant` do JWT

## Dependencias
GEST-001, GEST-005.

## Estimativa
M

## Prioridade
P5
