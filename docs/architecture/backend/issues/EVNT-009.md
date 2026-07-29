# EVNT-009 -- Implement boot-time dispatch retry sweep (recover queued/failed dispatches on startup)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
After a crash or restart, dispatches in `queued` or `failed` status (with `attempts < max`) may exist
in `notification_dispatch`. This issue adds a boot-time recovery sweep — an `ApplicationRunner` that
queries `DispatchRepositoryPort` for pending dispatches and re-submits them through
`NotificationOrchestrationService.retrySweep`. This ensures the "emergency never lost" invariant
(ADR-005, doc 06 risk #4) survives restarts.

## Spec de referencia
- SPEC-02 (fail-open notify + retry)

## Escopo
- [ ] Create `DispatchRetrySweepRunner` implementing `ApplicationRunner` in `application` package
- [ ] On start: query `DispatchRepositoryPort.findPendingDispatches()` (status in `queued`/`failed`, attempts < max budget)
- [ ] For each pending dispatch: call `NotificationOrchestrationService.retrySweep(dispatch)` — same flow as the EventoCriado listener retry path
- [ ] Log count of recovered dispatches at startup; emit a Micrometer counter `evnt.dispatch.sweep.recovered`
- [ ] Cap sweep to avoid thundering-herd: process at most N dispatches per sweep (configurable via `EVNT_DISPATCH_SWEEP_BATCH_SIZE`, default 50)
- [ ] Unit test: one pending dispatch → one retry call; exhausted dispatch (attempts >= max) → skipped; empty → no-op

## Resultado esperado
On startup, any pending dispatches from before the crash are re-submitted within the first few seconds.
Emergency dispatches are never permanently lost across restarts.

## Criterios de aceite
- [ ] At startup, `DispatchRetrySweepRunner` queries `notification_dispatch` for `status IN ('queued','failed')` and `attempts < max`
- [ ] Each found dispatch is submitted to `NotificationOrchestrationService.retrySweep`
- [ ] Dispatches with `attempts >= EVNT_DISPATCH_RETRY_MAX_ATTEMPTS` are skipped (already exhausted)
- [ ] Micrometer counter `evnt.dispatch.sweep.recovered` is incremented by the number of resubmitted dispatches
- [ ] Sweep is bounded by `EVNT_DISPATCH_SWEEP_BATCH_SIZE` (default 50) — no unbounded queries

## Dependencias
- EVNT-007 (OrchestrateNotification — retrySweep method)
- EVNT-010 (DispatchRepositoryPort adapter)

## Estimativa
S

## Prioridade
P2
