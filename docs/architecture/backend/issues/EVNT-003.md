# EVNT-003 -- Implement EventQueryController (GET /events, /events/{id}, /events/active) + DTOs

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Exposes the read-side HTTP surface: single-event fetch, active-event list (hydration + poll fallback
for the FE when SSE is unavailable), and paged event history with false-positive rate aggregate.
Also creates all response DTOs (`EventoDto`, `ActiveEventsDto`, `NotificationDto`) shared across
controllers. These endpoints are the caregiver panel's source of truth for current alarm state (FE ADR-005b).

## Spec de referencia
- SPEC-01 (API surface §2.2, §2.3, §2.7)
- SPEC-03 (live event delivery — poll fallback path)
- contracts/api-contract.md §3.2, §3.6

## Escopo
- [ ] Create `EventoDto`, `ActiveEventsDto`, `NotificationDto` record classes in `api.dto` package
- [ ] Create `EventQueryController` (`@RestController`, `/api/v1/events`)
- [ ] Implement `GET /events/{eventoId}` — single event fetch; `404` when not found or cross-tenant; delegate to `EventQueryService.findById`
- [ ] Implement `GET /events/active` — list active (unconfirmed + open dispatch); support `since` (ISO-8601) and `urgencia` query params; `activeEvent` field = highest-priority unconfirmed emergency; delegate to `EventQueryService.listActive`
- [ ] Implement `GET /events` — paged query (params: `pacienteId`, `comandoRef`, `feedback`, `urgencia`, `from`, `to`, `page`, `size`); optional `groupBy=comando` → include `falsePositiveRate` aggregate per `comandoRef`; delegate to `EventQueryService.listAll`
- [ ] Unit tests: findById found/not-found; listActive with/without `since`; listAll with/without `groupBy`

## Resultado esperado
All three read endpoints are reachable, return correct DTOs, and properly scope results to the
caregiver's tenant. Tests pass with service mocked.

## Criterios de aceite
- [ ] `GET /events/{eventoId}` returns `200` with `EventoDto` for known event in tenant; `404` for unknown or cross-tenant
- [ ] `GET /events/active` returns `activeEvent` (highest-urgency unconfirmed emergency) and `events[]` ordered emergency-first; `activeEvent: null` when none active
- [ ] `GET /events/active?since=<ISO>` returns only events changed at or after `since`
- [ ] `GET /events?groupBy=comando` includes `falsePositiveRate` per `comandoRef`
- [ ] Tenant isolation: event from a different tenant is not returned by any endpoint

## Dependencias
- EVNT-001 (scaffold)
- EVNT-005 (GlobalExceptionHandler)

## Estimativa
M

## Prioridade
P1
