# INT-EVNT-004 -- Contract tests EVNT -> ACS + define emergency escalation protocol (gap #3: secondary-caregiver fallback when primary unroutable)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
**Handoff gap #3 from M2.** Two concerns in one:
(a) **Contract test**: validates `CaregiverRoutingPort` → ACS (`findResponsavelByPaciente` + `findById`) with WireMock;
(b) **Emergency escalation protocol** (gap #3): what happens when the primary caregiver is unreachable or `CaregiverRoutingPort` times out on an `emergencia`? SPEC-10 says "fail closed + surface"; hub-integration.md §2.3 notes "escalation-to-secondary is an open item (doc 06 gap #3), out of MVP." This issue freezes the MVP decision (which will be "no secondary escalation: fail closed + alarm + metric") and registers it as a decision record, so the spec is complete.

## Spec de referencia
- SPEC-10 (integrations — ACS contract)
- contracts/hub-integration.md §2.3

## Escopo
- [ ] Create WireMock stubs: `acs/responsavel-found.json` (GET /pacientes/{id}/responsavel → 200 with caregiverId); `acs/responsavel-not-found.json` (→ 404); `acs/caregiver-by-id.json` (GET /cuidadores/{id} → 200); `acs/caregiver-unresolvable.json` (→ 404)
- [ ] Write `AcsContractTest`: routing found → caregiver resolved; 404 → `CaregiverUnresolvableException`; timeout → `UpstreamUnavailableException`
- [ ] **Escalation decision (gap #3):** document in ADR-008 (or new section in ADR-005): MVP behavior when primary caregiver is unreachable = fail closed, emit `evnt.dispatch.caregiver-unroutable` metric (Micrometer counter), log `ALARM:UNROUTABLE_EMERGENCY` for operator intervention. No automatic secondary escalation in MVP (deferred to v2 / broker-enabled phase).
- [ ] Update `contracts/hub-integration.md §2.3` with the decided fallback note

## Resultado esperado
ACS contract is verified; gap #3 is closed with a documented MVP decision (no secondary escalation)
and a surfaced metric for operator visibility.

## Criterios de aceite
- [ ] `AcsFeignAdapter.findResponsavelByPaciente` returns caregiver for found stub; throws on 404/timeout
- [ ] `AcsFeignAdapter.findById` returns caregiver for found stub; throws `ConfirmadorUnresolvedException` on 404
- [ ] ADR-008 (or §2.3 appendix) documents the "no secondary escalation in MVP" decision with rationale
- [ ] `evnt.dispatch.caregiver-unroutable` Micrometer counter is registered and incremented when `CaregiverUnresolvableException` occurs on the emergency path

## Dependencias
- EVNT-017 (AcsFeignAdapter must exist)

## Estimativa
M

## Prioridade
P5
