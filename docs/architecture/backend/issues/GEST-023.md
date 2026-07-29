# GEST-023 -- Wire Cucumber integration test infrastructure (Testcontainers PostgreSQL, Spring Security test stubs, tenant schema setup)

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto
As 4 features Gherkin (~54 cenarios) geradas em SPEC-08 precisam de infraestrutura de execucao Cucumber + Testcontainers. O container PostgreSQL sobe uma vez por suite, aplica a migration V0400 em um schema de teste, e e compartilhado entre todos os cenarios. A autenticacao OAuth2 e substituida por um `@TestConfiguration` com `SecurityMockMvcRequestPostProcessors.jwt()` configurado com os scopes corretos por cenario. Contexto de tenant injetado via header ou SpringMockMvc.

## Spec de referencia
- SPEC-08, event-contracts.md §3.1 (idempotency), setup-local.md §7

## Escopo
- [ ] implementar `backend/services/gest-service/src/test/java/com/neuroassist/gest/bdd/CucumberIntegrationTest.java` (runner: `@Suite`, `@IncludeEngines("cucumber")`, features em `classpath:features/`)
- [ ] implementar `backend/services/gest-service/src/test/resources/application-test.yml` (datasource Testcontainers, Flyway location apontando para V0400, jwk-set-uri desabilitado via mock)
- [ ] criar shared `AbstractGestBddIT.java` com `@SpringBootTest`, `@Testcontainers`, container PostgreSQL estatico + migration hook, `MockMvc` autowired
- [ ] implementar `TestJwtHelper.java` que gera JWTs de teste com scopes configurados (`gest.read`, `gest.manage`, `device`)
- [ ] garantir que cada cenario roda em schema isolado (rollback por cenario ou schema separado por cenario) para evitar interferencia

## Resultado esperado
Suite Cucumber compila e roda com `./gradlew :services:gest-service:test -Dcucumber.filter.tags="@integration"` sem falhar por falta de infraestrutura.

## Criterios de aceite
- [ ] `./gradlew :services:gest-service:test` com a tag `@integration` nao falha com NPE ou excecao de infraestrutura
- [ ] Testcontainers PostgreSQL sobe e migration V0400 e aplicada antes dos cenarios (log de migracao visivel)
- [ ] cenario com escopo `gest.manage` cria primitivo com sucesso; cenario com `gest.read` tentando criar retorna 403
- [ ] cada cenario parte de estado limpo (sem vazamento entre cenarios via schema isolation)

## Dependencias
GEST-013 (migration V0400), GEST-017 (SecurityConfig).

## Estimativa
M

## Prioridade
P8
