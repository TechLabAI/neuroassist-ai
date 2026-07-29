# GEST-FE-001 -- gest.contract.ts — MODULE_KEY, MODULE_MENU_METADATA, MODULE_PERMISSION_MAP, MODULE_FEATURE_FLAGS

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens
**GitHub:** [#TBD]

## Outcome
`libs/gest/shell-entry/src/lib/gest.contract.ts` exports all four constants that govern module identity, menu visibility, permissions and feature flags for GEST, enabling the shell to resolve GEST's contract without importing internals.

## Context / Problem statement
- GEST is an embedded module (showInNav: false) with a single permission (`gest.live-test.run`) and one feature flag (`edgeLiveBridge`). These must be declared in the canonical contract file before any other lib depends on them.
- Source: SPEC-05 (all sections).
- Bloqueia: GEST-FE-002, GEST-FE-004, GEST-FE-006, GEST-INT-001

## Approach (execution path)
1. Create `libs/gest/shell-entry/src/lib/gest.contract.ts` with `MODULE_KEY = 'gest' as const`.
2. Add `MODULE_MENU_METADATA` with `showInNav: false`, `label: 'Teste ao vivo do gesto'`, `icon: 'camera'`, `order: null`.
3. Add `MODULE_PERMISSION_MAP` with `'gest.live-test.run': ['CUIDADORA_FAMILIAR', 'CONDUCTOR']`.
4. Add `MODULE_FEATURE_FLAGS` with `edgeLiveBridge: { default: true }` (real stream vs. forced degraded/mock).
5. Export all from `libs/gest/shell-entry/src/index.ts`.

## Non-goals
- Not responsible for the descriptor object (GEST-FE-004) or routes (GEST-FE-002).
- Not responsible for enforcing the permission at runtime — that is GEST-FE-016.

## Layer(s)
frontend / tool entry

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-05-tool-contract.md` (§1..§5)

## Architecture alignment
- Pure `const` exports, no class, no Angular decorator
- Tag Nx: `scope:gest`, `type:domain`
- `showInNav: false` is load-bearing — prevents dead nav entry (SPEC-05 §2)

## Implementation checklist (by layer/file)

**Lib path**
- [ ] `libs/gest/shell-entry/src/lib/gest.contract.ts`
- [ ] `libs/gest/shell-entry/src/index.ts` (export contract)

**Tests**
- [ ] `npx nx test gest-shell-entry` PASS (verify MODULE_KEY, showInNav false, permission map keys)
- [ ] `npx nx lint gest-shell-entry` PASS
- [ ] `npx nx build gest-shell-entry` PASS

## Acceptance criteria
- `MODULE_MENU_METADATA.showInNav === false`
- `MODULE_PERMISSION_MAP['gest.live-test.run']` contains `CUIDADORA_FAMILIAR`
- `MODULE_FEATURE_FLAGS.edgeLiveBridge.default === true`
- `npx nx test gest-shell-entry` PASS
- `npx nx lint gest-shell-entry` PASS
- `npx nx build gest-shell-entry` PASS

## Dependencies
Nenhuma (foundation)

## Empresa-context impact
Nenhum — contract constants are global within the module scope, not tenant-specific.

## Milestone
Wave 1 / Foundation

## Labels
type:feature
layer:frontend
scope:gest
priority:P1

## Risks / Notes
- `edgeLiveBridge` flag must never produce a fake "recognized" when off — that invariant is enforced by the facade (GEST-FE-008), not this contract file

## Prioridade
P1
