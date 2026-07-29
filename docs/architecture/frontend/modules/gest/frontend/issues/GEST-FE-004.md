# GEST-FE-004 -- GEST_MODULE_DESCRIPTOR + gest.descriptor.ts (shell discovery entry)

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens
**GitHub:** [#TBD]

## Outcome
`libs/gest/shell-entry/src/lib/gest.descriptor.ts` exports `GEST_MODULE_DESCRIPTOR` — the full shell registration object including `liveTestComponent` (lazy resolver), `routes`, `permissionMap`, `featureFlags`, `menu`, and `bootstrap`. The shell can register GEST via this descriptor alone.

## Context / Problem statement
- The `scope:web` shell reads `GEST_MODULE_DESCRIPTOR` to know how to bootstrap GEST and how to obtain `GestureLiveTestComponent` for composition into host pages without directly importing `scope:gest`.
- The `liveTestComponent` field is the key enabler: it lets host pages load the component lazily through the shell, preserving nx-boundaries.
- Source: SPEC-06 §1, §5.
- Bloqueia: GEST-FE-006 (fallback descriptor in tool-descriptors.ts), GEST-INT-001 (shell integration service uses descriptor)

## Approach (execution path)
1. Create `libs/gest/shell-entry/src/lib/gest.descriptor.ts`.
2. Define `GEST_MODULE_DESCRIPTOR` with all fields per SPEC-06 §1.
3. Add `liveTestComponent: () => import('@neuroassist/gest').then(m => m.GestureLiveTestComponent)`.
4. Wire `entry`, `routes`, `permissionMap` (from GEST-FE-001), `featureFlags` (from GEST-FE-001), `menu` (from GEST-FE-001), `bootstrap` (from GEST-FE-005).
5. Export from `libs/gest/shell-entry/src/index.ts`.

## Non-goals
- Not responsible for the static fallback in `tool-descriptors.ts` (GEST-FE-006).
- Not responsible for implementing `gestBootstrap` (GEST-FE-005).

## Layer(s)
frontend / tool entry

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-06-shell-integration.md` (§1, §5)

## Architecture alignment
- `as const` object, no class
- Tag Nx: `scope:gest`, `type:domain`

## Implementation checklist (by layer/file)

**Lib path**
- [ ] `libs/gest/shell-entry/src/lib/gest.descriptor.ts`
- [ ] `libs/gest/shell-entry/src/index.ts` (export GEST_MODULE_DESCRIPTOR)

**Tests**
- [ ] `npx nx test gest-shell-entry` PASS (verify descriptor shape, liveTestComponent is a function)
- [ ] `npx nx lint gest-shell-entry` PASS
- [ ] `npx nx build gest-shell-entry` PASS

## Acceptance criteria
- `GEST_MODULE_DESCRIPTOR.menu.showInNav === false`
- `GEST_MODULE_DESCRIPTOR.liveTestComponent` is a `() => Promise<Type<GestureLiveTestComponent>>`
- `npx nx build gest-shell-entry` PASS

## Dependencies
- GEST-FE-001 (contract constants)
- GEST-FE-002 (GEST_ROUTES)
- GEST-FE-005 (gestBootstrap)

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
- The `liveTestComponent` lazy resolver is what breaks the nx-boundary violation — if host pages import `GestureLiveTestComponent` directly from `scope:gest` instead of via this resolver, the boundary is violated

## Prioridade
P1
