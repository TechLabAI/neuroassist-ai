# GEST-FE-021 -- Telemetry events — live-test start/stop/verdict/degraded via @neuroassist/shared/observability

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
`GestureTestFacade` emits telemetry events for `live-test-started`, `live-test-stopped`, `live-test-verdict` (recognized/not-recognized/timeout), and `live-test-degraded` via `@neuroassist/shared/observability`. No raw confidence or biometric data in any event payload.

## Context / Problem statement
- Observability enables the product team to track recognition rates, degraded mode frequency, and camera error frequency across tenants.
- Source: SPEC-03 §4 (no raw numbers), BE SPEC-07 (observability pattern).

## Approach (execution path)
1. In `GestureTestFacade`, inject `ObservabilityService` from `@neuroassist/shared/observability`.
2. On `startTest()` → emit `live-test-started` with `{ commandDraftRef, composite, degraded }`.
3. On `stopTest()` → emit `live-test-stopped` with `{ commandDraftRef, finalState }`.
4. On terminal state (`recognized`, `not-recognized`, timeout) → emit `live-test-verdict` with `{ commandDraftRef, recognized, degraded, composite }`.
5. On degraded activation → emit `live-test-degraded` with `{ reason }`.
6. No `confidence` values in any payload.

## Non-goals
- Not responsible for backend telemetry pipeline (that is BE observability scope).

## Layer(s)
frontend / data-access

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-04-data-access.md` (§3)

## Architecture alignment
- Events emitted via `shared/observability` abstraction
- Tag Nx: `scope:gest`, `type:data-access`

## Implementation checklist (by layer/file)

**Data-access**
- [ ] `libs/gest/data-access/src/lib/gesture-test.facade.ts`

**Tests**
- [ ] `npx nx test gest-data-access` PASS (events emitted on each state transition; no confidence in payload)
- [ ] `npx nx lint gest-data-access` PASS
- [ ] `npx nx build gest-data-access` PASS

## Acceptance criteria
- `live-test-started` emitted when `startTest()` called
- `live-test-verdict` emitted with `recognized` boolean and `degraded` boolean
- No `confidence` or raw numeric field in any telemetry payload
- `npx nx test gest-data-access` PASS

## Dependencies
- GEST-FE-008 (facade — events emitted from state transitions)

## Empresa-context impact
Nenhum — telemetry events are scoped to the session/tenant via auth context.

## Milestone
Wave 8 / Observability

## Labels
type:feature
layer:frontend
scope:gest
priority:P8

## Risks / Notes
- `@neuroassist/shared/observability` must exist before this issue is implemented; coordinate with the shared libs team

## Prioridade
P8
