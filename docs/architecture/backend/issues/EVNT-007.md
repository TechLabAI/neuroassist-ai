# EVNT-007 -- Implement OrchestrateNotification use case (EventoCriado listener, tier derivation, dispatch creation, fail-open retry)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Implements the `OrchestrateNotification` use case: an `@EventListener` on `EventoCriado` that
deterministically derives the notification tier (`emergencia → full_screen_alarm`, `rotina → discreet`),
resolves the responsible caregiver via ACS, creates a `notification_dispatch` record (status `queued`),
calls `NotificationDispatchPort` (DASH), and on failure marks the dispatch `failed` + re-queues.
Failure never rolls back the `Evento` (ADR-004 durable-first). This is the "always-notify" invariant
for emergency events (ADR-005).

## Spec de referencia
- SPEC-02 (OrchestrateNotification use case)

## Escopo
- [ ] Create `NotificationOrchestrationService` in `application` package with `@EventListener(EventoCriado.class)` method `orchestrate`
- [ ] Implement `NotificationTier.from(urgenciaSnapshot)` pure function — no branch may downgrade `emergencia`
- [ ] Resolve responsible caregiver: call `CaregiverRoutingPort.findResponsavelByPaciente(pacienteId)` — on timeout fail closed (log + record failure, do not silently proceed for emergencies)
- [ ] Persist `NotificationDispatch` record (`status=queued`) via `DispatchRepositoryPort.save`
- [ ] Call `NotificationDispatchPort.dispatch(dispatch, eventoSnapshot)` — on success: update to `delivered`, publish `NotificacaoDespachada`; on failure: update to `failed`, publish `NotificacaoFalhou`, re-queue (increment `attempts`)
- [ ] Enforce retry budget: when `attempts` exceeds `EVNT_DISPATCH_RETRY_MAX_ATTEMPTS`, leave dispatch `failed` and emit a surfaced failure metric (never silent)
- [ ] Unit tests: emergency → full_screen_alarm dispatch created; routine → discreet; DASH failure → dispatch.status=failed + re-queue; ACS timeout → failure recorded

## Resultado esperado
Every persisted `Evento` triggers at least one dispatch attempt; emergencies are never silently dropped;
tier derivation is a pure function with no external branch; DASH failures are durable and retried.

## Criterios de aceite
- [ ] Tier derivation: `emergencia → full_screen_alarm`, `rotina → discreet` — no other mapping is possible
- [ ] Dispatch record is persisted to `notification_dispatch` before any call to DASH
- [ ] DASH failure updates dispatch to `failed`, increments `attempts`, publishes `NotificacaoFalhou`, and schedules retry without rolling back the `Evento`
- [ ] `NotificacaoFalhou` with `exhausted=true` is emitted when retry budget is exceeded; dispatch stays `failed`
- [ ] ACS timeout on emergency path: failure is recorded and surfaced, not silently swallowed

## Dependencias
- EVNT-006 (EventoCriado event source)
- EVNT-010 (DispatchRepositoryPort adapter for integration; mocked for unit)
- EVNT-017 (AcsFeignAdapter + DashFeignAdapter for integration; mocked for unit)

## Estimativa
L

## Prioridade
P2
