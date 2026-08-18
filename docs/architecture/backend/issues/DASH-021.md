# DASH-021 -- Configure OAuth2 resource server: panel JWT validation (JWK set) + SVC_EVNT authority for /internal/**

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

O DASH tem dois planos de autenticacao: (1) `/panel/**` exige JWT de cuidador emitido pelo ACS,
validado localmente contra o JWK set; (2) `/internal/**` exige credencial service-to-service
com authority `SVC_EVNT` (emitido pelo ACS em client-credentials flow). Os dois planos devem
ser configurados de forma que o browser nunca acesse `/internal/**` atraves do GWY (rota nao
exposta publicamente pelo GWY).

## Spec de referencia

- SPEC-06 (JWT panel, SVC_EVNT authority, JWK set local validation; invariant: browser nao alcanca /internal/**)
- `api-contract.md §8` (auth summary)

## Escopo

- [ ] `SecurityConfig extends WebSecurityConfigurerAdapter` (ou `SecurityFilterChain`): antMatchers `/internal/**` com authority `SVC_EVNT`; `/panel/**` e `/actuator/health` configurados; todos os outros → `403`
- [ ] `spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${DASH_OAUTH2_JWK_SET_URI}` para validacao local de JWTs do painel
- [ ] Claims extraidos: `sub` (caregiverId), `tenant` (tenantId), `authorities` (roles) — CustomJwtAuthenticationConverter
- [ ] Teste de seguranca: `/panel/summary` sem JWT → `401`; com JWT invalido → `401`; `/internal/notifications` com JWT de cuidador (sem SVC_EVNT) → `403`; `/internal/notifications` com SVC_EVNT → aceito
- [ ] `tenant` claim ausente ou nao parseavel → `401` (invariant de seguranca)

## Resultado esperado

As duas superficies de autenticacao isoladas; cuidador nao consegue chamar `/internal/**`.

## Criterios de aceite

- [ ] `/panel/**` sem JWT → `401`
- [ ] `/panel/**` com JWT invalido (assinatura errada) → `401`
- [ ] `/panel/**` com JWT valido mas `tenant` ausente → `401`
- [ ] `/internal/notifications` com JWT de cuidador (sem authority SVC_EVNT) → `403`
- [ ] `/internal/notifications` com SVC_EVNT → `202`

## Dependencias

- DASH-001 (estrutura Spring Boot, dependencia spring-security-oauth2-resource-server)
- DASH-016 (JWK set URI configurada)

## Estimativa

M

## Prioridade

P6
