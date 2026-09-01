# CONV-021 -- Criar outbox-retry-sweep.feature (Gherkin para sweep de retry do outbox — SPEC-09 sem cobertura BDD)

**Status:** INTACT -- gerado 2026-08-31
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto

O SPEC-09 define o sweep de retry do outbox (`@Scheduled`) como comportamento obrigatório, mas
nenhum dos 5 arquivos `.feature` existentes cobre este comportamento. Esta issue cria o arquivo
Gherkin `outbox-retry-sweep.feature` e os step definitions correspondentes, garantindo que o
mecanismo de retry seja testado de ponta a ponta no BDD (incluindo transição de status e
esgotamento do budget). Esta issue formaliza o gap identificado no input do autopilot.

## Spec de referencia

- SPEC-09 (messaging-architecture — sweep de retry, budget, pendente/enviado/descartado)
- `contracts/data-model.md` §2.4 (AuditOutbox invariantes)

## Escopo

- [ ] Criar `src/test/resources/features/outbox-retry-sweep.feature` com cenários: (1) HIST retorna OK → outbox linha vai para ENVIADO; (2) HIST retorna 503 3× → linha permanece PENDENTE com tentativas=3; (3) HIST retorna 503 5× (budget esgotado) → linha vai para DESCARTADO; (4) sweep com `clientEventId` duplicado já ENVIADO → linha ignorada (idempotente)
- [ ] Criar `OutboxSweepSteps.java` em `bdd/`: steps de "given an audit fact in outbox with status PENDING", "when the sweep runs", "then the outbox entry status is {string}", "and the attempt count is {int}"
- [ ] Steps não duplicam `the response status is {int}` de `SharedSteps.java`
- [ ] Integrar com `OutboxRetrySweepService` via invocação direta (não via `@Scheduled` em teste — chamar `sweep()` manualmente no step "when the sweep runs")

## Resultado esperado

Comportamento do sweep coberto por BDD; `./gradlew :conv-service:test` com `outbox-retry-sweep.feature` passando.

## Criterios de aceite

- [ ] `./gradlew :conv-service:test` BUILD SUCCESS com `outbox-retry-sweep.feature` 100% PASS
- [ ] Cenário de DESCARTADO após budget esgotado presente e passando
- [ ] Cenário de idempotência (linha já ENVIADO não reprocessada) presente
- [ ] Step "when the sweep runs" chama `OutboxRetrySweepService.sweep()` diretamente (não aguarda `@Scheduled` trigger)

## Dependencias

- CONV-017 (OutboxRetrySweepService)
- CONV-019 (CucumberSpringContext)

## Estimativa

S

## Prioridade

P9
