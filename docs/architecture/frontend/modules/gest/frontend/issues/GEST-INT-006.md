# GEST-INT-006 -- Onboarding shell-page composition slot — /onboarding/gesto hosts <gest-live-test>

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
`apps/web/src/app/onboarding/onboarding-gesto-step.component.ts` renders `<gest-live-test>` via `GestShellIntegrationService.resolveLiveTestSlot()` for the onboarding gesture step, following the same composition pattern as GEST-INT-005.

## Context / Problem statement
- The onboarding gesture step (`/onboarding/gesto`) is the second production composition point. The conductor runs the live test here during initial setup.
- Same nx-boundary requirement: no direct `scope:gest` import from `scope:web` onboarding code via static import.
- Source: SPEC-01 §2, SPEC-06 §5.

## Approach (execution path)
1. In `onboarding-gesto-step.component.ts`, inject `GestShellIntegrationService`.
2. Resolve the slot lazily and render `GestureLiveTestComponent` with a `commandDraft` for the onboarding gesture under test.
3. Wire `(testCompleted)` to advance the onboarding stepper.
4. No direct `@neuroassist/gest` import in this file.

## Non-goals
- Not responsible for the onboarding stepper flow (that is the ACS/onboarding module territory).

## Layer(s)
frontend / integration (shell composition)

## Bounded context
gest (composition side: scope:web)

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-06-shell-integration.md` (§5)

## Architecture alignment
- `scope:web`, dynamic component outlet
- Tag Nx: `scope:web`, `type:feature`

## Implementation checklist (by layer/file)

**Shell / web app**
- [ ] `apps/web/src/app/onboarding/onboarding-gesto-step.component.ts`

**Tests**
- [ ] `npx nx lint web` PASS
- [ ] `npx nx build web` PASS

## Acceptance criteria
- `onboarding-gesto-step.component.ts` has no direct `scope:gest` import
- `GestureLiveTestComponent` rendered inside onboarding step
- `npx nx lint web` PASS

## Dependencies
- GEST-INT-001 (GestShellIntegrationService)

## Empresa-context impact
Nenhum.

## Milestone
Wave 4 / Hub integrations

## Labels
type:integration
layer:frontend
scope:gest
priority:P4

## Risks / Notes
- Onboarding module must be scaffolded before this slot can be wired — track ACS/onboarding backlog dependency

## Prioridade
P4
