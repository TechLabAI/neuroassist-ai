# GEST-FE-006 -- tool-descriptors.ts — static GEST fallback entry at scope:web shell

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
`apps/web/src/app/config/tool-descriptors.ts` contains a static GEST fallback descriptor entry that mirrors `GEST_MODULE_DESCRIPTOR` (with `showInNav: false` and `liveTestComponent` resolver), ensuring the shell builds even if dynamic discovery is unavailable.

## Context / Problem statement
- The shell needs a static fallback for every registered module so `nx build web` succeeds at compile time.
- For GEST the critical field is `liveTestComponent` — the host pages resolve it through this descriptor entry.
- Source: SPEC-06 §2.
- Bloqueia: GEST-INT-005, GEST-INT-006 (PAC/onboarding host pages resolve slot through this file)

## Approach (execution path)
1. Open `apps/web/src/app/config/tool-descriptors.ts` and add the GEST entry following the existing pattern for other modules.
2. Set `showInNav: false` and `menu.order: null`.
3. Add `liveTestComponent` field pointing to the lazy resolver.
4. Keep the `permissionMap` and `featureFlags` references aligned with `GEST-FE-001` constants.

## Non-goals
- Not responsible for dynamic shell discovery (that is GestShellIntegrationService, GEST-INT-001).

## Layer(s)
frontend / shell integration

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-06-shell-integration.md` (§2)

## Architecture alignment
- Static object, no class
- Tag Nx: `scope:web` (lives in apps/web)

## Implementation checklist (by layer/file)

**Shell config**
- [ ] `apps/web/src/app/config/tool-descriptors.ts`

**Tests**
- [ ] `npx nx build web` PASS (static fallback enables build)
- [ ] `npx nx lint web` PASS

## Acceptance criteria
- GEST entry present in `tool-descriptors.ts` with `showInNav: false`
- `liveTestComponent` field is a lazy resolver function
- `npx nx build web` PASS

## Dependencies
- GEST-FE-001 (permission map and feature flags constants)
- GEST-FE-004 (GEST_MODULE_DESCRIPTOR shape reference)

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
- If `liveTestComponent` is missing or `null`, host pages in PAC/onboarding will fail at composition slot resolution at runtime

## Prioridade
P1
