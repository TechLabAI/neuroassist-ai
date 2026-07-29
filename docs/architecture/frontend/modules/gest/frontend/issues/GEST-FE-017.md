# GEST-FE-017 -- gestDeviceOnlineGuard — paired+online device check → degraded mode, never blocking (SPEC-01 §3)

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
`libs/gest/access/src/lib/gest-device-online.guard.ts` implements `gestDeviceOnlineGuard` as a non-blocking route guard: it checks whether a paired, online `DispositivoEdge` exists for the current patient, and — if not — activates the facade's degraded mode instead of denying the route.

## Context / Problem statement
- An offline device must NOT block the caregiver from previewing the live-test surface. The guard's non-blocking semantics are load-bearing (ADR-005: degraded is honest, never blocking).
- The guard returns `true` always; it only signals the facade to enter degraded mode when no device is online.
- Source: SPEC-01 §3.

## Approach (execution path)
1. Create `gest-device-online.guard.ts` implementing Angular `CanActivateFn`.
2. Call an injectable `DeviceStatusService` (shared or created here) to check device pairing/online status.
3. If no device online → inject `GestureTestFacade` and call `setDeviceOffline()` (or equivalent) to pre-set degraded mode.
4. Always return `true` (never block the route).
5. Write unit test with both online and offline device scenarios.

## Non-goals
- Not responsible for the `authGuard` (shared/access-decision, already exists).
- Not responsible for the degraded banner rendering (GEST-FE-015).

## Layer(s)
frontend / access guards

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-01-route-map.md` (§3)

## Architecture alignment
- `CanActivateFn` (functional guard pattern, Angular 17+)
- Never returns `false` or `UrlTree`
- Tag Nx: `scope:gest`, `type:domain`

## Implementation checklist (by layer/file)

**Access lib**
- [ ] `libs/gest/access/src/lib/gest-device-online.guard.ts`

**Tests**
- [ ] `npx nx test gest-access` PASS (online → degraded false; offline → degraded true; always allows route)
- [ ] `npx nx lint gest-access` PASS
- [ ] `npx nx build gest-access` PASS

## Acceptance criteria
- Guard ALWAYS allows the route (returns `true`)
- When no online device → facade `degraded` signal becomes `true`
- `npx nx test gest-access` PASS

## Dependencies
- GEST-FE-008 (GestureTestFacade — called to set degraded)

## Empresa-context impact
Nenhum — device check uses session + tenant from auth token.

## Milestone
Wave 5 / Access guards

## Labels
type:feature
layer:frontend
scope:gest
priority:P5

## Risks / Notes
- If the device status API is unavailable, guard must default to degraded mode (fail-safe, not fail-open)

## Prioridade
P5
