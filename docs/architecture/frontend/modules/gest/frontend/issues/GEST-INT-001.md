# GEST-INT-001 -- GestShellIntegrationService — registerWithShell + resolveLiveTestSlot

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens
**GitHub:** [#TBD]

## Outcome
`libs/gest/integration/src/lib/gest-shell-integration.service.ts` implements `GestShellIntegrationService` with `registerWithShell()` and `resolveLiveTestSlot()`. `scope:web` host pages can obtain `GestureLiveTestComponent` lazily without a direct `scope:gest` import, preserving nx-boundaries.

## Context / Problem statement
- This is the ONLY sanctioned path for PAC/onboarding host pages to obtain `GestureLiveTestComponent`. Direct imports of `scope:gest` from `scope:pac` or `scope:acs` would violate the nx-boundary (doc 02 §4, R6 doc 06).
- Source: SPEC-06 §3, §5.
- Bloqueia: GEST-INT-005, GEST-INT-006 (host page composition depends on this service)

## Approach (execution path)
1. Create `gest-shell-integration.service.ts` with `Injectable({ providedIn: 'root' })`.
2. `registerWithShell()`: publish `GEST_MODULE_DESCRIPTOR` to the shell's module registry (via shared shell registry service).
3. `resolveLiveTestSlot()`: return `Promise<Type<GestureLiveTestComponent>>` by calling `GEST_MODULE_DESCRIPTOR.liveTestComponent()`.
4. This service lives in `scope:gest` → consumed only by `scope:web` (allowed by nx-boundary rule).
5. Export from `libs/gest/integration/src/index.ts`.

## Non-goals
- Not responsible for the static fallback descriptor (GEST-FE-006).

## Layer(s)
frontend / integration

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-06-shell-integration.md` (§3, §5)

## Architecture alignment
- `Injectable({ providedIn: 'root' })`
- Tag Nx: `scope:gest`, `type:domain`

## Implementation checklist (by layer/file)

**Integration lib**
- [ ] `libs/gest/integration/src/lib/gest-shell-integration.service.ts`
- [ ] `libs/gest/integration/src/index.ts`

**Tests**
- [ ] `npx nx test gest-integration` PASS
- [ ] `npx nx lint gest-integration` PASS
- [ ] `npx nx build gest-integration` PASS

## Acceptance criteria
- `resolveLiveTestSlot()` returns a `Type<GestureLiveTestComponent>` without a direct import from `scope:gest` at PAC/ACS level
- `npx nx lint gest-integration` PASS (boundary check)
- `npx nx build gest-integration` PASS

## Dependencies
- GEST-FE-004 (GEST_MODULE_DESCRIPTOR)

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
- If the shell registry service does not yet exist (depends on shell architecture), `registerWithShell()` may be a no-op stub initially — coordinate with the shell team

## Prioridade
P4
