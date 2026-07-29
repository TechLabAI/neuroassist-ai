# GEST-FE-005 -- gest.bootstrap.ts — init (detection-contract negotiation) / destroy (stream teardown)

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens
**GitHub:** [#TBD]

## Outcome
`libs/gest/shell-entry/src/lib/gest.bootstrap.ts` exports `gestBootstrap` with `init()` that negotiates the detection-contract version and memoizes it for the session, and `destroy()` that guarantees no `MediaStream` track leaks when the module unloads.

## Context / Problem statement
- `init()` must NOT open the camera — only negotiate the contract version via `GET /api/v1/gest/detection-contract` (privacy-by-default, doc 01 §6). Camera is opened only on explicit user action.
- `destroy()` is the last-line guarantee: if `GestureTestFacade` failed to stop its stream for any reason, `destroy()` cleans it up.
- Source: SPEC-06 §4.
- Bloqueia: GEST-FE-004 (descriptor references gestBootstrap)

## Approach (execution path)
1. Create `libs/gest/shell-entry/src/lib/gest.bootstrap.ts`.
2. In `init()`: call `DetectionContractService.getContract()`, memoize `contractVersion` in a module-level signal/variable. Prepare `bridgeToken` provider (GEST-INT-004). No camera.
3. In `destroy()`: call `GestureTestFacade.stopTest()` if any instance is live; release memoized contract descriptor.
4. Export `gestBootstrap` from `index.ts`.
5. Wire into `GEST_MODULE_DESCRIPTOR.bootstrap` (GEST-FE-004).

## Non-goals
- Not responsible for `GestEligibilityService` (GEST-FE-009).
- Camera lifecycle during a test is managed by the facade (GEST-FE-008).

## Layer(s)
frontend / tool entry

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-06-shell-integration.md` (§4)

## Architecture alignment
- No class, exported plain object `{ init, destroy }`
- Tag Nx: `scope:gest`, `type:domain`

## Implementation checklist (by layer/file)

**Lib path**
- [ ] `libs/gest/shell-entry/src/lib/gest.bootstrap.ts`
- [ ] `libs/gest/shell-entry/src/index.ts` (export gestBootstrap)

**Tests**
- [ ] `npx nx test gest-shell-entry` PASS (init does not call getUserMedia; destroy cleans up)
- [ ] `npx nx lint gest-shell-entry` PASS
- [ ] `npx nx build gest-shell-entry` PASS

## Acceptance criteria
- `init()` calls `GET /api/v1/gest/detection-contract` exactly once per session
- `init()` does NOT call `navigator.mediaDevices.getUserMedia`
- `destroy()` stops all MediaStream tracks (confirmed by GEST-FE-024 Playwright scenario)
- `npx nx build gest-shell-entry` PASS

## Dependencies
- GEST-FE-009 (DetectionContractService called from init)
- GEST-INT-004 (bridgeToken provider prepared in init)

## Empresa-context impact
Nenhum.

## Milestone
Wave 1 / Foundation

## Labels
type:feature
layer:frontend
scope:gest
priority:P1

## Risks / Notes
- `init()` network failure (detection-contract 404/5xx) must degrade gracefully: session proceeds without memoized version, degraded mode activates in facade
- e2e scenario `composition-boundary.feature` (GEST-FE-025) asserts bootstrap init does NOT open the camera

## Prioridade
P1
