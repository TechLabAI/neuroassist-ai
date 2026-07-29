# INT-EVNT-006 -- Contract tests EVNT -> HIST (WireMock: AuditTrailPort EventoCriado/EventoConfirmado/FalsoPositivoMarcado fact shapes)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Validates the EVNT → HIST audit trail contract (`AuditTrailPort`) with WireMock. Three fact types
must be validated: `EventoCriado` (after ingress), `EventoConfirmado` / `FalsoPositivoMarcado`
(after confirmation). Validates that the `factType`, `eventoId`, `actor`, `at`, `payload`, and
`correlationId` fields match the shape HIST expects (otherwise HIST silently ignores malformed facts).

## Spec de referencia
- SPEC-10 (integrations — HIST contract)
- contracts/hub-integration.md §2.5

## Escopo
- [ ] Create WireMock stubs: `hist/audit-ok.json` (POST /api/v1/audit-facts → 200)
- [ ] Write `HistAuditTrailContractTest`: after ingress, verify WireMock captured a POST with `factType=EventoCriado`, `eventoId`, `correlationId`; after confirmation, verify `factType=EventoConfirmado` or `FalsoPositivoMarcado` with `actor` (caregiverId) and `confirmadoAt`
- [ ] Verify `X-Correlation-Id` header is present on every HIST call
- [ ] Verify that a HIST `500` does NOT propagate to the ingress or confirmation HTTP response (retryable, not on critical path)

## Resultado esperado
Every audit-relevant EVNT action produces a correctly shaped HIST fact, and a HIST failure never
breaks the confirmation response.

## Criterios de aceite
- [ ] Ingress action: WireMock captures `POST /api/v1/audit-facts` with `factType=EventoCriado` and `eventoId` + `correlationId` matching the created event
- [ ] Confirmation: captured fact has `factType=EventoConfirmado` (or `FalsoPositivoMarcado`), `actor=caregiverId`, `at=confirmadoAt`
- [ ] HIST `500` stub: `HistFeignAdapter.appendFact` throws retryable exception; confirmation HTTP response is still `200`

## Dependencias
- EVNT-017 (HistFeignAdapter must exist)
- EVNT-008 (ConfirmEvento — calls HIST)

## Estimativa
S

## Prioridade
P8
