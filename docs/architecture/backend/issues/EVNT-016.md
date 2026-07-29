# EVNT-016 -- Implement PacFeignAdapter (CommandLookupPort, snapshot resolve, 422 on deleted Comando)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Implements `CommandLookupPort` as a Feign client calling PAC's `GET /api/v1/comandos/{comandoRef}`.
EVNT uses the response to snapshot `label`, `urgencia`, and `pacienteId` at the moment of event
creation — these values are stored in `evento.command_label_snapshot` and `evento.urgencia_snapshot`
and never re-read from PAC (ADR-004). A deleted or deactivated `Comando` returns `404`/`410` and
must produce a `422 command-unresolvable` on the ingress path with no `Evento` created.

## Spec de referencia
- SPEC-10 (integrations — PAC contract)
- contracts/hub-integration.md §2.2

## Escopo
- [ ] Create `CommandLookupClient` Feign interface: `GET /api/v1/comandos/{comandoRef}` → `ComandoSnapshot` (record with `label`, `urgencia`, `pacienteId`, `sensibilidade`)
- [ ] Create `PacFeignAdapter` implementing `CommandLookupPort`: `200` → return snapshot; `404`/`410` → throw `CommandUnresolvableException`; timeout on `emergencia` → throw `UpstreamUnavailableException`
- [ ] Propagate `X-Correlation-Id` and tenant context to PAC
- [ ] Unit test with WireMock: 200 with label/urgencia/pacienteId; 404 → `CommandUnresolvableException`; 504 → `UpstreamUnavailableException`

## Resultado esperado
`CommandLookupPort` returns the `Comando` snapshot or throws a typed exception that the ingress
service maps to `422 command-unresolvable` or `502/504` as appropriate.

## Criterios de aceite
- [ ] PAC `200` → `ComandoSnapshot` returned with `label`, `urgencia`, `pacienteId`, `sensibilidade` populated
- [ ] PAC `404`/`410` → `CommandUnresolvableException` thrown; ingress service returns `422 /errors/gest-rejected` (doc: command-unresolvable)
- [ ] PAC timeout on `emergencia` → `UpstreamUnavailableException` → ingress returns `502/504`
- [ ] `urgencia` field from snapshot is the value written to `evento.urgencia_snapshot`

## Dependencias
- EVNT-001 (CommandLookupPort interface)
- EVNT-006 (IngestRecognizedReport — calls this port to snapshot Comando)

## Estimativa
S

## Prioridade
P5
