# EVNT-022 -- Implement BDD Cucumber: ingress golden path (emergency full_screen_alarm + routine discreet dispatch)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Implements step definitions for the "ingress golden path" Cucumber feature already authored in M2
(`tests/cucumber/ingress-golden-path.feature`). Scenarios cover: (a) edge sends an `emergencia`
report → `201`, `full_screen_alarm` dispatch created, SSE frame emitted; (b) edge sends a `rotina`
report → `201`, `discreet` dispatch created. Uses Testcontainers PostgreSQL + WireMock stubs for
GEST/PAC/ACS/DASH/HIST.

## Spec de referencia
- SPEC-08 (BDD test scenarios)
- tests/cucumber/ingress-golden-path.feature

## Escopo
- [ ] Implement step definitions for `ingress-golden-path.feature` in `infrastructure.test.cucumber.steps` package
- [ ] Configure Testcontainers PostgreSQL + WireMock stubs (GEST validate OK, PAC returns `urgencia=emergencia` Comando, ACS returns caregiver, DASH returns ack) in the Cucumber Spring context
- [ ] Assert: POST /reports returns 201; `notification_dispatch` row exists with `tier=full_screen_alarm`; `evento.feedback=nao_avaliado`
- [ ] Repeat for `rotina`: same but `tier=discreet`
- [ ] All four Gherkin scenarios from `ingress-golden-path.feature` pass with `./gradlew :services:evnt-service:test --tests '*CucumberRunner*'`

## Resultado esperado
The ingress golden path BDD feature runs end-to-end against a real DB and WireMock, confirming the
full orchestration from `POST /reports` to dispatch record creation.

## Criterios de aceite
- [ ] `./gradlew :services:evnt-service:test` includes and passes the ingress-golden-path Cucumber feature
- [ ] Emergency scenario: `notification_dispatch.tier = full_screen_alarm`, `notification_dispatch.status IN ('queued','delivered')`
- [ ] Routine scenario: `notification_dispatch.tier = discreet`
- [ ] All step definitions exist (no undefined steps — no yellow in Cucumber report)
- [ ] Testcontainers PostgreSQL starts; WireMock stubs match GEST/PAC/ACS/DASH contracts

## Dependencias
- EVNT-006 (IngestRecognizedReport must be complete)
- EVNT-007 (OrchestrateNotification must be complete)
- EVNT-010 (JPA adapters)
- EVNT-015, EVNT-016, EVNT-017 (Feign adapters — WireMocked in test)

## Estimativa
M

## Prioridade
P8
