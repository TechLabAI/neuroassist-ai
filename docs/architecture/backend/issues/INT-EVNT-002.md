# INT-EVNT-002 -- Contract tests EVNT -> GEST (WireMock: validation success + 422 reject + 502 fail-closed on emergency timeout)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Validates the EVNT → GEST contract (`GestValidationPort`) with WireMock stubs representing the real
`gest-service` behavior. Three scenarios must be contractually validated: (1) valid report → `200
{valid:true, profileVersion}`; (2) GEST rejects → `422` → no `Evento` created; (3) GEST timeout on
emergencia → `502/504` surfaced by EVNT. The stubs here become the reference WireMock mappings reused
across all BDD tests in EVNT-022.

## Spec de referencia
- SPEC-10 (integrations — GEST contract)
- contracts/hub-integration.md §2.1

## Escopo
- [ ] Create WireMock stub file `wiremock/gest-service/validate-ok.json` (POST /api/v1/validations → 200 `{valid:true}`)
- [ ] Create WireMock stub `validate-reject.json` (POST → 422 `{valid:false, reason:"unknown-primitive"}`)
- [ ] Create WireMock stub `validate-timeout.json` (POST → delayed 6000ms response to trigger read timeout)
- [ ] Write integration test class `GestValidationContractTest`: start WireMock server; inject `GestFeignAdapter`; call validate with each stub scenario; assert adapter behavior matches SPEC-10
- [ ] Verify `X-Correlation-Id` is present in captured WireMock request for all scenarios

## Resultado esperado
Contract between EVNT and GEST is validated for all three scenarios; WireMock stubs are reusable by
EVNT-022 BDD tests without duplication.

## Criterios de aceite
- [ ] `GestFeignAdapter.validate` returns valid result for `validate-ok.json` stub
- [ ] `GestFeignAdapter.validate` throws `GestRejectedException` for `validate-reject.json` stub
- [ ] `GestFeignAdapter.validate` throws `UpstreamUnavailableException` for `validate-timeout.json` stub (timeout < 6000ms)
- [ ] WireMock captures show `X-Correlation-Id` header in every outbound EVNT→GEST call

## Dependencias
- EVNT-015 (GestFeignAdapter must exist to run the contract test)

## Estimativa
M

## Prioridade
P5
