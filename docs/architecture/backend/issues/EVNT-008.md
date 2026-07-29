# EVNT-008 -- Implement ConfirmEvento use case (terminal feedback, ACS confirmador resolve, HIST append, EventoConfirmado/FalsoPositivoMarcado)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Implements the `ConfirmEvento` application service that closes the feedback loop: validates the
`Evento` is not already terminal, resolves the `confirmadoPor` caregiver via ACS (from the JWT
identity — never from the body), sets `feedback` (`real` or `falso_positivo`) with `confirmadoPor`
+ `confirmadoAt`, appends an audit fact to HIST, and publishes `EventoConfirmado` or
`FalsoPositivoMarcado`. ADR-006: the verdict is terminal — a correction is a new audited action,
never an in-place overwrite.

## Spec de referencia
- SPEC-02 (ConfirmEvento use case)

## Escopo
- [ ] Create `EventConfirmationService` in `application` package
- [ ] `confirm(eventoId, verdict, caregiverJwtIdentity)`: load `Evento` (404 if not found or cross-tenant); check `feedback == nao_avaliado` (409 if terminal + different verdict; 200 idempotent if same); resolve caregiver via `CaregiverRoutingPort.findById(caregiverJwtIdentity)` (422 if unresolvable); set `feedback`, `confirmadoPor`, `confirmadoAt` + `updatedAt` in `@Transactional` block; publish `EventoConfirmado` or `FalsoPositivoMarcado`; append audit to HIST via `AuditTrailPort` (retryable, not on critical path)
- [ ] `acknowledgeDispatch(eventoId, dispatchId)`: load dispatch (404); assert `status == delivered` (409 otherwise); update to `acknowledged`
- [ ] Domain exceptions: `EventoNotFoundException`, `FeedbackTerminalException`, `ConfirmadorUnresolvedException`
- [ ] Unit tests: confirm real → EventoConfirmado published; falso_positivo → FalsoPositivoMarcado; already-terminal (different verdict) → 409; same verdict re-submit → 200; ACS unresolvable → 422; HIST failure → logged but does not fail the confirmation

## Resultado esperado
`ConfirmEvento` sets feedback exactly once per `Evento`, requires attribution from ACS, audits via HIST,
and publishes the terminal domain event. HIST failure is retried without blocking the response.

## Criterios de aceite
- [ ] `feedback` transitions from `nao_avaliado` to `real`/`falso_positivo` exactly once per `Evento`
- [ ] `confirmadoPor` and `confirmadoAt` are always set together with a non-null verdict (matches the DB CHECK constraint)
- [ ] Re-submitting the *same* verdict returns `200` without a second audit fact or domain event
- [ ] A *different* verdict on a terminal event returns `409 /errors/feedback-terminal`
- [ ] `EventoConfirmado` or `FalsoPositivoMarcado` is published exactly once per confirmation
- [ ] HIST `AuditTrailPort` failure does not roll back the verdict or block the response

## Dependencias
- EVNT-006 (Evento domain model + EventoRepositoryPort)
- EVNT-010 (JPA adapters for integration; mocked for unit)
- EVNT-017 (AcsFeignAdapter + HistFeignAdapter for integration; mocked for unit)

## Estimativa
M

## Prioridade
P2
