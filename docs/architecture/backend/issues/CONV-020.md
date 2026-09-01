# CONV-020 -- Criar step definitions para speech-tree-navigation, node-selection-and-audio, session-revocation-and-scope e gesture-binding-resolution

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

Cobre os 4 arquivos `.feature` restantes com step definitions concretos, usando a infraestrutura
BDD criada em CONV-019. Cada arquivo de feature mapeia para um arquivo de steps:
- `SpeechTreeSteps.java` ← `speech-tree-navigation.feature`
- `NodeSelectionSteps.java` ← `node-selection-and-audio.feature`
- `SessionRevocationSteps.java` ← `session-revocation-and-scope.feature`
- `GestureBindingSteps.java` ← `gesture-binding-resolution.feature`
Convenções D-492: sem aspas escapadas nos Cucumber Expressions; steps de status somente em
`SharedSteps`; `{string}` e `{int}` para parâmetros.

## Spec de referencia

- SPEC-08 (bdd-test-scenarios — convenções, mapeamento feature→SPEC)
- `tests/cucumber/` (arquivos .feature como fonte de verdade)

## Escopo

- [ ] Criar `SpeechTreeSteps.java`: steps de GET /tree, verificação de `n-51` como `pending_locale`, `n-42` como selecionável, rootOnly, locale inválido → 400
- [ ] Criar `NodeSelectionSteps.java`: steps de POST /select com áudio presente/ausente (degraded), seleção de `categoria` → 409, `content-pending-locale` → 409, `clientEventId` idempotente
- [ ] Criar `SessionRevocationSteps.java`: steps de token revogado → 401, token sem scope → 403, ACS indisponível → 401 (WireMock stub para blacklist)
- [ ] Criar `GestureBindingSteps.java`: steps de POST /bindings/resolve com `gest:blink-double` → `bound: true`, ref desconhecido → `bound: false`, VocabularyPort indisponível → `bound: false` (sem 500)
- [ ] Garantir que nenhum step duplica `the response status is {int}` (já em SharedSteps.java)
- [ ] Executar `./gradlew :conv-service:test` com todos os cenários dos 5 features passando (incluindo `assisted-semantic-search.feature` do CONV-019)

## Resultado esperado

Todos os 5 arquivos .feature com steps cobertos; `./gradlew :conv-service:test` com Cucumber 100% PASS.

## Criterios de aceite

- [ ] `./gradlew :conv-service:test` BUILD SUCCESS com 0 cenários FAILED ou UNDEFINED
- [ ] Relatório Cucumber mostra cobertura dos 5 arquivos de feature
- [ ] Nenhum arquivo de steps contém aspas escapadas `\"` em Cucumber Expressions (grep)
- [ ] `SessionRevocationSteps` usa WireMock para simular ACS indisponível (não mocka diretamente o Spring Security)
- [ ] `GestureBindingSteps` verifica que `bound: false` retorna `200` (não 5xx) quando VocabularyPort falha

## Dependencias

- CONV-019 (CucumberSpringContext + SharedSteps + infraestrutura BDD)

## Estimativa

L

## Prioridade

P8
