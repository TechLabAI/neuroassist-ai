# GEST-FE-003 -- GestModule barrel — re-exports, Nx tags, project.json

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
`libs/gest/shell-entry/src/index.ts` is the public barrel for `@neuroassist/gest`, re-exporting `GestureLiveTestComponent`, `GEST_ROUTES`, `GEST_MODULE_DESCRIPTOR`, `gestBootstrap`, and the contract constants. All Nx lib `project.json` files for the seven gest libs exist with correct tags.

## Context / Problem statement
- The `@neuroassist/gest` package must expose a clean barrel so the `scope:web` shell can lazy-load `GestureLiveTestComponent` and resolve the descriptor without touching internal paths.
- Seven libs need `project.json` files: `gest/shell-entry`, `gest/data-access`, `gest/feature-live-test`, `gest/access`, `gest/integration`, `gest/navigation`, `gest/ui`.
- Source: SPEC-02 §3, SPEC-06 §1.
- Bloqueia: GEST-INT-001 (shell registration uses the barrel)

## Approach (execution path)
1. Create/update `libs/gest/shell-entry/src/index.ts` barrel with all public exports.
2. For each of the 7 libs, create `project.json` with correct `name`, `tags: ["scope:gest", "type:<...>"]`, and `sourceRoot`.
3. Ensure `tsconfig.base.json` at the monorepo root has the `@neuroassist/gest` path alias pointing to `libs/gest/shell-entry/src/index.ts`.
4. Run `nx reset` to clear Nx cache after tags change.

## Non-goals
- Not responsible for implementing the exported symbols (each has its own issue).
- Not responsible for nx-boundary ESLint rules (GEST-INT-002).

## Layer(s)
frontend / tool entry

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§3)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-06-shell-integration.md` (§1)

## Architecture alignment
- Tag Nx: `scope:gest`, `type:app` (shell-entry), `type:feature`/`type:data-access`/`type:domain`/`type:ui` for others
- Barrel exports only: no runtime logic in `index.ts`

## Implementation checklist (by layer/file)

**Lib path**
- [ ] `libs/gest/shell-entry/src/index.ts`
- [ ] `libs/gest/shell-entry/project.json`

**Tests**
- [ ] `npx nx lint gest-shell-entry` PASS (boundary tags)
- [ ] `npx nx build gest-shell-entry` PASS

## Acceptance criteria
- `import { GestureLiveTestComponent } from '@neuroassist/gest'` resolves
- All 7 libs have `project.json` with `scope:gest` tag
- `npx nx lint gest-shell-entry` PASS
- `npx nx build gest-shell-entry` PASS

## Dependencies
- GEST-FE-001 (contract exported from barrel)

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
- Missing path alias in `tsconfig.base.json` will cause build failures in the shell before any component is imported

## Prioridade
P1
