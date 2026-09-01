# CONV-011 -- Criar AcsSessionAdapter — Feign para acs-service (validação JWT + consulta blacklist por request)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

Implementa `AcsSessionPort` como adapter Feign para `acs-service`. A validação do JWT é feita
pelo Spring Security Resource Server (configurado em CONV-015) via `jwk-set-uri`. O adapter
complementa com a **consulta à blacklist em toda requisição** (ADR-003 — sessão não-expirável
revogável imediatamente). Falha do ACS → fail-closed (rejeita `401`). Ponto de extensão para
cache de blacklist com TTL curto (risco R3, abordado em CONV-022) — a interface do adapter deve
ser projetada para suportar cache sem alterar a camada de aplicação.

## Spec de referencia

- SPEC-06 (security — ACS blacklist, fail-closed)
- `contracts/hub-integration.md` §2

## Escopo

- [ ] Criar `infrastructure/integration/acs/AcsSessionFeignClient.java` (`@FeignClient(name="acs-service", url="${neuroassist.acs.url}")`) com operação `isSessionRevoked(String sessionId): boolean` (POST ou GET para o endpoint de blacklist do ACS)
- [ ] Criar `infrastructure/integration/acs/AcsSessionAdapter.java` implementando `AcsSessionPort` — `validateAndCheckBlacklist(bearerToken)`: extrai `sub` do JWT, chama `AcsSessionFeignClient.isSessionRevoked(sub)`, lança `SessionRevokedException` se `true`
- [ ] Configurar `neuroassist.acs.url` em `application.yml` e stub em `application-test.yml` (Learning D-533 — Feign com `url=` precisa de valor em testes para não falhar na inicialização)
- [ ] Criar WireMock stub `WireMockBlacklistStub.java` para testes BDD (responde `isRevoked: false` por padrão; override por cenário)
- [ ] Configuração de circuit breaker (Resilience4j) básica para o `AcsSessionFeignClient` — não é o lugar de cache (ver CONV-022)
- [ ] Teste unitário do adapter: token válido + ACS responde `isRevoked: false` → pass; token na blacklist → `SessionRevokedException`; ACS unavailable → `UnauthorizedException` (fail-closed)

## Resultado esperado

Blacklist consultada em toda requisição; ACS indisponível → rejeição (`401`); nenhuma requisição sem identidade validada passa.

## Criterios de aceite

- [ ] `./gradlew :conv-service:test` BUILD SUCCESS
- [ ] Teste unitário: blacklist retorna `false` → sem exceção
- [ ] Teste unitário: blacklist retorna `true` → `SessionRevokedException`
- [ ] Teste unitário: Feign lança `FeignException` → `UnauthorizedException` (fail-closed, não 500)
- [ ] `neuroassist.acs.url` stub presente em `application-test.yml` (Learning D-533)

## Dependencias

- CONV-004 (AcsSessionPort)

## Estimativa

M

## Prioridade

P4
