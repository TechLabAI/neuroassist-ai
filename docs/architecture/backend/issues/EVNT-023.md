# EVNT-023 -- Implement BDD Cucumber: idempotent ingress (duplicate reportId 200 OK) + terminal confirmation (409 on re-verdict)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Implements step definitions for the idempotency and confirmation Cucumber features
(`idempotent-ingress.feature`, `caregiver-confirmation.feature`) authored in M2. Scenarios:
(a) duplicate `reportId` → second `POST /reports` returns `200` with the original `EventoDto`,
no new `Evento`, no new dispatch; (b) caregiver confirms `real` → `200` with feedback set;
(c) re-submitting the same verdict → `200` (idempotent); (d) submitting a different verdict after
terminal → `409`.

## Spec de referencia
- SPEC-08 (BDD test scenarios)
- tests/cucumber/idempotent-ingress.feature, tests/cucumber/caregiver-confirmation.feature

## Escopo
- [ ] Implement step definitions for `idempotent-ingress.feature`: first POST → 201; second POST with same `reportId` → 200 with original body; verify `evento` count = 1; `report_idempotency` count = 1
- [ ] Implement step definitions for `caregiver-confirmation.feature`: create event → POST confirmation `real` → 200 feedback=real, confirmadoPor set; same verdict again → 200; different verdict → 409
- [ ] WireMock stub for ACS `findById(caregiverId)` → caregiver record
- [ ] WireMock stub for HIST `appendFact` → 200 (verify audit called once per verdict)
- [ ] All scenarios in both feature files pass in CI

## Resultado esperado
Idempotency and confirmation invariants are validated end-to-end: no accidental double-event creation,
no second audit for re-submitted identical verdict, and terminal state is enforced at the API level.

## Criterios de aceite
- [ ] Idempotent ingress: exactly one `evento` row and one `report_idempotency` row exist after two identical POST /reports
- [ ] Confirmation: `evento.feedback` transitions to `real` exactly once; WireMock HIST stub received exactly one audit-fact call
- [ ] Re-submitted identical verdict: returns `200`, no second HIST call, no second domain event
- [ ] Conflicting verdict: returns `409 /errors/feedback-terminal`
- [ ] All Cucumber scenarios defined in the two feature files pass

## Dependencias
- EVNT-006 (idempotency logic)
- EVNT-008 (ConfirmEvento)
- EVNT-010 (JPA adapters)
- EVNT-017 (AcsFeignAdapter + HistFeignAdapter — WireMocked in test)

## Estimativa
M

## Prioridade
P8
