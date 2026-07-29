# INT-GEST-002 -- Integration with ACS service — OAuth2 JWT validation via jwk-set-uri

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto
`gest-service` nao chama ACS diretamente via Feign. A integracao e via `jwk-set-uri` do Spring Security OAuth2 Resource Server: o servico faz fetch periodico das chaves publicas JWK do ACS para validar assinaturas de tokens JWT de cuidadores. O `acs-service` e descoberto via REG. Em testes, a URI e substituida por uma key publica de teste embutida.

## Spec de referencia
- SPEC-06, hub-integration.md §4, api-contract.md §6.1

## Escopo
- [ ] validar que `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` aponta para `http://acs-service/oauth2/jwks` (via REG, sem hardcode de porta) em producao
- [ ] validar que token JWT emitido pelo ACS e aceito pelo `gest-service` (teste de integracao com token real ou stub)
- [ ] validar que token JWT com `tenant` claim incorreto retorna `403 /errors/tenant-mismatch` (GEST verifica apos validar assinatura)

## Resultado esperado
JWT valido emitido pelo ACS e aceito; JWT com assinatura invalida ou expirado retorna 401; claim tenant divergente retorna 403.

## Criterios de aceite
- [ ] token com assinatura valida e scope `gest.read` → `200` em GET /tipos-gesto
- [ ] token com assinatura invalida → `401 Unauthorized`
- [ ] token expirado → `401 Unauthorized`
- [ ] token com `tenant` claim diferente do tenant no path → `403 /errors/tenant-mismatch`

## Dependencias
GEST-017 (SecurityConfig).

## Estimativa
S

## Prioridade
P5
