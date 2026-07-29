# EDGE-012 -- Configure OAuth2 JWT resource server (jwk-set-uri via ACS/REG, scope enforcement: edge.read / edge.manage / edge.device / edge.internal)

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
O `edge-service` e um OAuth2 Resource Server. Valida JWTs contra `jwk-set-uri` do ACS (descoberto via REG). Ha quatro tipos de token: human (edge.read/edge.manage), device (edge.device com claim deviceId+tenant), service (edge.internal), e nenhum (POST /enroll — guarda pelo enrollment token, nao JWT). SPEC-06 governa o scope enforcement.

## Spec de referencia
- SPEC-06

## Escopo
- [ ] configurar `spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://acs-service/oauth2/jwks` no `application.yml` (resolvido via REG)
- [ ] criar `infrastructure/config/SecurityConfig.java` com `SecurityFilterChain`: permitAll em `/actuator/health`, `/actuator/info`, `POST /api/v1/edge/devices/enroll`; exigir autenticacao em todos os demais
- [ ] implementar `JwtScopeValidator` (verifica scope por endpoint: `edge.read`, `edge.manage`, `edge.device`, `edge.internal`)
- [ ] implementar `DeviceTokenExtractor` para extrair `deviceId` e `tenantId` dos claims do device JWT (nao do body)
- [ ] criar testes unitarios com `@WithMockJwt` testando cada scope e o endpoint de enroll sem JWT

## Resultado esperado
Requisicoes sem token valido retornam 401; requisicoes com scope errado retornam 403; POST /enroll aceita sem Bearer.

## Criterios de aceite
- [ ] 401 Unauthorized em endpoint protegido sem token
- [ ] 403 Forbidden com scope incorreto (ex: `edge.read` em rota `edge.manage`)
- [ ] POST /devices/enroll aceito sem Authorization header
- [ ] `deviceId` extraido corretamente de device token com claim `deviceId`
- [ ] `tenantId` derivado exclusivamente dos claims do JWT, nunca do body

## Dependencias
- EDGE-004 (precede)

## Estimativa
M

## Prioridade
P5
