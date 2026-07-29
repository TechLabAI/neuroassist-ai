# GEST-INT-002 -- Nx-boundary ESLint rules — scope:gest import restrictions (PAC/ACS must not import scope:gest)

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
`.eslintrc.json` (or `eslint.config.js`) enforces that `scope:pac` and `scope:acs` may NOT import `scope:gest` directly. The only path for composition is via `GestShellIntegrationService` at `scope:web`. `nx lint` fails if the boundary is violated.

## Context / Problem statement
- Without this rule, a developer at PAC or ACS could accidentally import `GestureLiveTestComponent` directly, bypassing the composition pattern and creating a hard coupling.
- Source: SPEC-06 §5, SPEC-02 §4.

## Approach (execution path)
1. Identify existing boundary rules structure in `.eslintrc.json` / `nx.json`.
2. Add depConstraint: `sourceTag: "scope:pac"`, `onlyDependOnLibsWithTags: [...existing..., NOT "scope:gest"]`.
3. Same for `scope:acs`.
4. Add rule: `sourceTag: "scope:web"` CAN depend on `scope:gest` (for the composition host pages).
5. Run `nx lint gest-integration` and `nx lint pac-...` to confirm rules fire correctly.

## Non-goals
- Not responsible for implementing the integration service (GEST-INT-001).

## Layer(s)
frontend / integration

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-06-shell-integration.md` (§5)

## Architecture alignment
- ESLint `@nx/enforce-module-boundaries`
- Tag Nx: workspace-level config

## Implementation checklist (by layer/file)

**Workspace config**
- [ ] `.eslintrc.json`
- [ ] `libs/gest/integration/src/index.ts`

**Tests**
- [ ] `npx nx lint gest-integration` PASS
- [ ] `npx nx lint` (root) includes GEST boundary rules

## Acceptance criteria
- `scope:pac` directly importing `scope:gest` → `nx lint` error
- `scope:web` importing `scope:gest` → allowed
- `npx nx lint` PASS with new rules active

## Dependencies
- GEST-FE-003 (all 7 libs have correct Nx tags)

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
- Must verify existing PAC/ACS/onboarding code does not already violate this rule before enabling — remediate first if it does

## Prioridade
P4
