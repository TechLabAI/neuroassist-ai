# EVNT-004 -- Implement ConfirmationController (POST /events/{id}/confirmation + dispatches/{id}/ack)

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Exposes the caregiver write-side: the terminal verdict endpoint (`POST /events/{id}/confirmation`)
and the delivery acknowledgement endpoint (`POST /events/{id}/dispatches/{dispatchId}/ack`).
Both require `evnt:confirm` scope (enforced in EVNT-018). The confirmation endpoint receives only
the `verdict` field; the caregiver identity is resolved from the JWT (never trusted from the body — ADR-006).

## Spec de referencia
- SPEC-01 (API surface §2.5, §2.6)
- contracts/api-contract.md §3.4, §3.5

## Escopo
- [ ] Create `ConfirmationRequestDto` record with `@NotNull String verdict` validated against `["real","falso_positivo"]`
- [ ] Create `ConfirmationController` (`@RestController`, `/api/v1/events`)
- [ ] Implement `POST /events/{eventoId}/confirmation`: extract caregiver identity from JWT, call `EventConfirmationService.confirm(eventoId, verdict, caregiverId)`; return `200` with updated `EventoDto`; `404` unknown; `409` already-terminal (different verdict); `422` unresolvable `confirmadoPor`; `200` on idempotent re-submit of *same* verdict
- [ ] Implement `POST /events/{eventoId}/dispatches/{dispatchId}/ack`: call `EventConfirmationService.acknowledgeDispatch`; return `200`; `409` if dispatch not in `delivered` status
- [ ] Unit tests: valid verdict → 200; same verdict re-submit → 200 (idempotent); different verdict when terminal → 409; unknown event → 404

## Resultado esperado
Confirmation and ack endpoints are functional; verdict is extracted from JWT and never from body;
idempotency and terminal-state guard are enforced.

## Criterios de aceite
- [ ] `POST /events/{id}/confirmation` with `verdict=real` returns `200` with `feedback=real`, `confirmadoPor`, `confirmadoAt`
- [ ] Re-submitting the *same* verdict on a terminal event returns `200` (idempotent)
- [ ] Submitting a *different* verdict on a terminal event returns `409 Conflict` with `type=/errors/feedback-terminal`
- [ ] `confirmadoPor` is derived from the session JWT, never from the request body
- [ ] `POST /events/{id}/dispatches/{dispatchId}/ack` on a non-`delivered` dispatch returns `409`

## Dependencias
- EVNT-001 (scaffold)
- EVNT-005 (GlobalExceptionHandler)
- EVNT-018 (OAuth2 scope enforcement — needed before integration tests; unit tests mock auth)

## Estimativa
S

## Prioridade
P1
