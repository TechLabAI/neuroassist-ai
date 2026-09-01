# TREI-014 -- OAuth2 Resource Server config + schema-per-empresa tenant routing

**Status:** INTACT -- gerado 2026-09-01
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/7 itens

## Contexto

TREI é um OAuth2 Resource Server: valida JWT por assinatura (`jwk-set-uri`) + cheque de
revogação por request (AcsSessionPort, TREI-015). O schema-per-empresa routing troca o
schema PostgreSQL por request: `TenantContextHolder` extrai a `empresa` do JWT e
`TenantSchemaRoutingDataSource` direciona o datasource para `t_<empresa>`.
**Pitfall D-492:** `application-test.yml` DEVE usar `jwk-set-uri` (static test JWKS),
NUNCA `issuer-uri` — `issuer-uri` tenta OIDC discovery contra Keycloak ausente nos testes.

## Spec de referencia

- SPEC-06 (security — OAuth2 Resource Server, scopes, fail-closed)
- api-contract.md §4 (authentication + required claims: sub, empresa, pacienteId, scope)
- Learning D-492 (jwk-set-uri pitfall em application-test.yml)

## Escopo

- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/security/SecurityConfig.java` — `@Configuration @EnableWebSecurity`; configura resource server com `jwk-set-uri`; protege todos os endpoints com escopos corretos; `/health` livre de auth
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/security/TreiJwtAuthConverter.java` — extrai `empresa`, `pacienteId`, `scope` do JWT para o `Authentication`; popula `SecurityContext`
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/security/TenantContextHolder.java` — `ThreadLocal<UUID>` para `empresa`; preenchido pelo filter de auth; limpo ao final do request
- [ ] criar `src/main/java/com/neuroassist/trei/infrastructure/security/TenantSchemaRoutingDataSource.java` — `AbstractRoutingDataSource` que retorna lookup key `t_<empresa>` baseado em `TenantContextHolder.get()`
- [ ] criar `src/main/resources/application-test.yml` — com `spring.security.oauth2.resourceserver.jwt.jwk-set-uri: http://localhost:8090/test-jwks` (static, sem OIDC discovery); NÃO setar `issuer-uri` (D-492)
- [ ] verificar que `POST /nos/{noId}/aprovacoes/paciente` exige scope `treinamento.no.aprovar.paciente` e rejeita scope `treinamento.no.aprovar.cuidadora` com 403
- [ ] criar `src/test/java/com/neuroassist/trei/infrastructure/security/SecurityConfigTest.java` — testa que endpoint protegido retorna 401 sem JWT e 403 com JWT de scope errado; `/health` acessível sem auth

## Resultado esperado

Resource server configurado; schema routing funcional; application-test.yml usando
jwk-set-uri; scopes enforçados por endpoint.

## Criterios de aceite

- [ ] `./gradlew :trei-service:test --tests "*SecurityConfigTest*"` sai 0
- [ ] `application-test.yml` contém `jwk-set-uri` e NÃO contém `issuer-uri` (verificação textual)
- [ ] `GET /health` retorna 200 sem `Authorization` header (livre de auth)
- [ ] `POST /nos/{noId}/aprovacoes/paciente` com token de caregiver retorna 403 (scope mismatch, ADR-003)

## Dependencias

- TREI-004 (controllers — devem existir para os testes de segurança compilarem)

## Estimativa

M

## Prioridade

P5
