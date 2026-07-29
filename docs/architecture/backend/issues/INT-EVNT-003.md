# INT-EVNT-003 -- Contract tests EVNT -> PAC (WireMock: CommandLookupPort resolve + 422 on deleted/deactivated Comando)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Validates the EVNT → PAC contract (`CommandLookupPort`) with WireMock. Two scenarios: (1) active
`Comando` → `200` with `{label, urgencia, sensibilidade, pacienteId}`; (2) deleted/deactivated
`Comando` → `404` → EVNT returns `422 command-unresolvable`. Confirms that `urgencia_snapshot` in
`evento` comes from PAC's response, not from any EVNT default.

## Spec de referencia
- SPEC-10 (integrations — PAC contract)
- contracts/hub-integration.md §2.2

## Escopo
- [ ] Create WireMock stub `pac/comando-found.json` (GET /api/v1/comandos/{ref} → 200 with label/urgencia/sensibilidade/pacienteId)
- [ ] Create WireMock stub `pac/comando-deleted.json` (GET → 404)
- [ ] Write `PacCommandLookupContractTest`: valid ref → snapshot populated; deleted ref → `CommandUnresolvableException`
- [ ] Assert `urgencia_snapshot` in persisted `Evento` matches the `urgencia` from PAC response (end-to-end with Testcontainers DB)

## Resultado esperado
PAC contract is verified for both scenarios; the `urgencia_snapshot` provenance is confirmed.

## Criterios de aceite
- [ ] `PacFeignAdapter.findByRef` returns `ComandoSnapshot` with all fields for `comando-found.json` stub
- [ ] `PacFeignAdapter.findByRef` throws `CommandUnresolvableException` for `comando-deleted.json` stub
- [ ] `evento.urgencia_snapshot` is set from PAC response `urgencia` — not from any EVNT default

## Dependencias
- EVNT-016 (PacFeignAdapter must exist)

## Estimativa
S

## Prioridade
P5
