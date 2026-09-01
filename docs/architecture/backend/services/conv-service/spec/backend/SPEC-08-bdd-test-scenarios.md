# SPEC-08 — BDD Test Scenarios

## Objetivo
Mapear os cenários Gherkin (`tests/cucumber/*.feature`) para as SPECs e fixar convenções de step
definitions que evitam os defeitos recorrentes do Learning Layer (D-492).

## Escopo
**In:** catálogo de features, steps reutilizáveis, dados de exemplo, convenções do contexto Cucumber.
**Out:** os arquivos `.feature` em si (Gherkin puro; prosa descritiva fica aqui).

## Features (Gherkin puro — `tests/cucumber/`)
| Feature | Cobre | SPEC |
|---|---|---|
| `speech-tree-navigation.feature` | `/tree`, locale-invariância, pending-locale, guards | SPEC-01/02/11 |
| `assisted-semantic-search.feature` | kNN, fail-open, clamp de `k` | SPEC-03/12 |
| `node-selection-and-audio.feature` | seleção, fallback Web Speech, idempotência, outbox | SPEC-02/05/12 |
| `session-revocation-and-scope.feature` | blacklist, escopos, fail-closed ACS | SPEC-06 |
| `gesture-binding-resolution.feature` | `engineFeatureRef` opaco, degradação | SPEC-10 |

## Convenções de step definitions (Learning D-492 — aplicar no scaffold/BDD)
- **Sem aspas escapadas** em Cucumber Expressions; usar placeholders `{string}` / `{int}` (o parser
  falha com `\"`). Os literais nos `.feature` são escritos sem aspas por isso.
- **Sem `<param>` em `Scenario` plano** — `<nome>` só é permitido em `Scenario Outline` (é substituído
  pelos `Examples`); em `Scenario` normal vira literal `<nome>`, que não bate contra `{string}` ou `(.*)`.
  Substituir por step descritivo não-parametrizado (ex.: "sends a search request with no intent text").
  Aplicado em `assisted-semantic-search.feature` (D-492 validate pass).
- **Consolidar steps de status de resposta** (`the response status is {int}`) em `SharedSteps.java`;
  **não** duplicar a definição em cada arquivo (evita passos ambíguos/duplicados).
- **`messaging.enabled = false`** → o `CucumberSpringContext` **não** precisa de `@EmbeddedKafka`.
- **`application-test.yml`** com `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (JWKS estático);
  nunca `issuer-uri` em teste.

## Dados de exemplo
- Paciente `p-1024`, tenant `clinica-acme`, árvore com locales `pt,en`.
- Nós: `n-root` (categoria), `n-42` (frase "I want water with ice", ativo pt+en, áudio presente),
  `n-51` (frase, ativo pt / pendente en), `gest:blink-double` → `n-42`.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL keep one `.feature` per behavior area with reusable response-status steps centralized in `SharedSteps`.
- THE SYSTEM SHALL express step parameters via `{string}`/`{int}` placeholders and SHALL NOT use escaped quotes in Cucumber Expressions.
- WHERE messaging is disabled, THE Cucumber Spring context SHALL boot without `@EmbeddedKafka`.
