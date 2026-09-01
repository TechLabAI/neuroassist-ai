# CONV-019 -- Criar CucumberSpringContext + SharedSteps.java + application-test.yml + AssistedSearchSteps.java (step D-492)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/7 itens

## Contexto

Infraestrutura de BDD para o CONV. Quatro regras críticas do Learning Layer (D-492) que DEVEM
ser aplicadas nesta issue:
1. `application-test.yml` → `jwk-set-uri` estático (nunca `issuer-uri`)
2. Sem `@EmbeddedKafka` no `CucumberSpringContext` (`messaging.enabled = false`)
3. Steps de status HTTP (`the response status is {int}`) APENAS em `SharedSteps.java` — nunca
   duplicados em outros arquivos
4. **Novo step D-492:** `sends a search request with no intent text` (step descritivo, sem
   `<param>` em `Scenario` não-Outline) em `AssistedSearchSteps.java`

Inclui dados de exemplo canônicos: paciente `p-1024`, tenant `clinica-acme`, nós `n-root`,
`n-42` (ativo pt+en), `n-51` (ativo pt / pendente en), `gest:blink-double → n-42`.

## Spec de referencia

- SPEC-08 (bdd-test-scenarios — convenções de step defs, dados de exemplo)
- `tests/cucumber/assisted-semantic-search.feature` (feature que contém o step novo D-492)

## Escopo

- [ ] Criar `src/test/java/com/neuroassist/conv/bdd/CucumberSpringContext.java` — `@CucumberContextConfiguration @SpringBootTest(webEnvironment=RANDOM_PORT)` SEM `@EmbeddedKafka` (Learning D-492); carrega `application-test.yml`
- [ ] Criar `src/test/java/com/neuroassist/conv/bdd/SharedSteps.java` — step `the response status is {int}` (único no projeto); steps de header `X-Correlation-Id`; setup de auth token de paciente com `{sub, empresa, pacienteId, locale, scope}` necessários
- [ ] Criar `src/test/java/com/neuroassist/conv/bdd/AssistedSearchSteps.java` — step `sends a search request with no intent text` (usa intent vazio `""`, verifica `400 invalid-request`) derivado da feature `assisted-semantic-search.feature` linha com `<blank_intent>` corrigida (D-492)
- [ ] Criar `src/test/java/com/neuroassist/conv/bdd/support/TestDataFixture.java` — setup dos dados canônicos: inserção de `conv_arvore_fala`, `conv_no_arvore_fala`, `conv_no_conteudo` para `p-1024`/`clinica-acme`/`n-root`/`n-42`/`n-51` em test container PostgreSQL
- [ ] Criar `src/test/resources/application-test.yml` com `jwk-set-uri`, WireMock ports para ACS/PAC/HIST, `neuroassist.acs.url`, `neuroassist.pac.url`, `neuroassist.hist.url` (Learning D-533)
- [ ] Verificar que `./gradlew :conv-service:test` executa os cenários Cucumber sem "step not found" e sem falha de contexto

## Resultado esperado

Infraestrutura BDD inicializada; contexto Spring carrega sem erro; `AssistedSearchSteps` cobre o step novo corretamente.

## Criterios de aceite

- [ ] `./gradlew :conv-service:test` BUILD SUCCESS com Cucumber executando sem "undefined step"
- [ ] `CucumberSpringContext` não contém `@EmbeddedKafka` (grep confirma)
- [ ] `SharedSteps.java` contém o único `the response status is {int}` no projeto (ArchUnit ou grep)
- [ ] `AssistedSearchSteps` contém step `sends a search request with no intent text` sem `<param>` (D-492 — sem `<blank_intent>` em `Scenario` plano)
- [ ] `application-test.yml` contém `jwk-set-uri` e os 3 URLs de stub (ACS, PAC, HIST) — grep confirma

## Dependencias

- CONV-015 (SecurityConfig — necessário para o contexto Spring carregar com OAuth2)
- CONV-009 (JPA repositories — TestDataFixture usa os repositórios para setup)

## Estimativa

M

## Prioridade

P8
