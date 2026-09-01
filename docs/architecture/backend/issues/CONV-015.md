# CONV-015 -- Configurar OAuth2 Resource Server (jwk-set-uri, escopos conv.arvore.navegar/conv.no.selecionar, rejeição de autoridade caregiver)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

Configura o CONV como OAuth2 Resource Server puro para o ator *paciente com sessão* (ADR-003/ADR-012).
A validação de assinatura JWT é via `jwk-set-uri` (realm Keycloak compartilhado). O ACS é a segunda
camada (blacklist em toda requisição, CONV-011). Nenhuma autoridade de cuidadora/admin é aceita em
tokens CONV — rejeitar `403` mesmo que a scope CONV esteja presente. O interceptador de correlação
injeta `X-Correlation-Id` em toda chamada downstream. Configurado em `SecurityConfig.java`.

**Learning D-492 (crítico):** `application-test.yml` usa `jwk-set-uri` com JWKS estático — NUNCA
`issuer-uri` (dispara OIDC discovery contra Keycloak não disponível em teste, quebrando JPA init).

## Spec de referencia

- SPEC-06 (security — OAuth2 Resource Server, escopos, caregiver rejection)
- `contracts/api-contract.md` §4

## Escopo

- [ ] Criar `infrastructure/security/SecurityConfig.java` (`@Configuration @EnableWebSecurity`): `httpSecurity.oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwkSetUri(jwkSetUri)))` com `SecurityFilterChain`
- [ ] Criar `infrastructure/security/ConvJwtAuthConverter.java` — extrai claims `empresa`, `pacienteId`, `locale`, `scope` do JWT; rejeita tokens com `ROLE_CAREGIVER` / `ROLE_ADMIN` antes de qualquer scope check
- [ ] Anotar controllers com `@PreAuthorize("hasAuthority('SCOPE_conv.arvore.navegar')")` / `"SCOPE_conv.no.selecionar"` conforme endpoint
- [ ] Criar `infrastructure/security/CorrelationIdFilter.java` — extrai `X-Correlation-Id` do request (ou gera UUID se ausente), propaga via `MDC` e como header nas respostas downstream (Feign interceptor)
- [ ] Configurar `application-test.yml` com `spring.security.oauth2.resourceserver.jwt.jwk-set-uri: http://localhost:${wiremock.server.port}/jwks` (Learning D-492)
- [ ] Testes unitários de segurança: token sem scope → 403; token com scope + autoridade caregiver → 403; token válido com scope correto → pass

## Resultado esperado

Todos os endpoints CONV só acessíveis com JWT válido + scope correto; caregiver/admin rejeitado; `X-Correlation-Id` propagado.

## Criterios de aceite

- [ ] `./gradlew :conv-service:test` BUILD SUCCESS
- [ ] Teste unitário: JWT sem `conv.arvore.navegar` em `GET /tree` → `403 forbidden-scope`
- [ ] Teste unitário: JWT com scope válido + authority `ROLE_CAREGIVER` → `403 forbidden-scope`
- [ ] Teste unitário: JWT expirado ou inválido → `401 unauthenticated`
- [ ] `application-test.yml` contém `jwk-set-uri` (não `issuer-uri`) — verificável por grep
- [ ] `X-Correlation-Id` ecoado em todas as respostas (verificável com `@ExtendWith(MockMvcExtension)` no teste)

## Dependencias

- CONV-011 (AcsSessionAdapter — segunda camada de validação)

## Estimativa

M

## Prioridade

P5
