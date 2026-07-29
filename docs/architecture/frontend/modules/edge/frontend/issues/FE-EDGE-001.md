## [FE-EDGE-001] edge.contract.ts — MODULE_KEY, menu metadata, permission map, feature flags

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/edge/shell-entry/src/lib/edge.contract.ts` exports all four public contracts consumed by the shell:
`MODULE_KEY = 'edge'`, `MODULE_MENU_METADATA` (showInNav:false), `MODULE_PERMISSION_MAP` (read vs manage),
`MODULE_FEATURE_FLAGS` (polling cadence + thresholds as live tunable parameters — not compile-time constants).

**Context / Problem statement:**
- The shell resolves module identity, permission gates and feature flags from the module contract without
  importing internals. This file is the single source of those exports (SPEC-05).
- `showInNav:false` prevents a dead top-level menu entry; connectivity is always composed via chip.
- Feature flags (`pollingEnabled`, `pollIntervalMs`, `stalenessThresholdMs`, `offlineWindowMs`, `rePairEnabled`)
  are tunable without redeploy — the file must export them as `const` objects so a runtime override layer can
  shadow them.
- Bloqueia: FE-EDGE-002, FE-EDGE-003, FE-EDGE-015, INT-FE-EDGE-004

**Approach (execution path):**
1. Create `libs/edge/shell-entry/src/lib/edge.contract.ts` with the four exported constants.
2. Type `MODULE_PERMISSION_MAP` values as `ReadonlyArray<string>` so TypeScript enforces no mutation.
3. Export `MODULE_FEATURE_FLAGS` as `const` satisfying the shared `FeatureFlagMap` interface (from
   `@neuroassist/shared/feature-flags`).
4. Add barrel re-export in `libs/edge/shell-entry/src/index.ts`.
5. `nx lint edge-shell-entry` must pass (no stray imports outside `scope:edge` + `scope:shared`).

**Non-goals:**
- Does not implement any runtime override mechanism (that lives in the shared feature-flag service).
- Does not register with the shell (that is FE-EDGE-003 / INT-FE-EDGE-004).

**Layer(s):**
frontend / libs/edge/shell-entry

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-05-tool-contract.md` (§1–§5)

**Architecture alignment:**
- Standalone + OnPush + Signals (N/A for this file — pure TS)
- Tag Nx: `scope:edge`, `type:app` (shell-entry is entry-type per generator-config.yml)
- `showInNav:false` — ADR-006 composed module pattern

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/shell-entry/src/lib/edge.contract.ts` (create)
- 🔲 `libs/edge/shell-entry/src/index.ts` (barrel re-export MODULE_KEY, MODULE_MENU_METADATA, MODULE_PERMISSION_MAP, MODULE_FEATURE_FLAGS)

**Tests**
- 🔲 `libs/edge/shell-entry/src/lib/edge.contract.spec.ts` — unit: MODULE_KEY = 'edge'; showInNav = false; pollingEnabled.default = true; stalenessThresholdMs.default < offlineWindowMs.default (threshold invariant)
- 🔲 `nx test edge-shell-entry` PASS (Jest unit)
- 🔲 `nx lint edge-shell-entry` PASS (boundaries + ESLint)

**Milestone:**
Wave P1 / Foundation

**Labels:**
type:feature
layer:frontend
scope:edge
priority:P1

**Acceptance criteria:**
- `npx nx test edge-shell-entry` PASS
- `npx nx lint edge-shell-entry` PASS
- `MODULE_KEY === 'edge'`, `showInNav === false`, `stalenessThresholdMs.default (45000) < offlineWindowMs.default (90000)` asserted in tests
- No `scope:web` or `scope:acs` imports in this file

**Dependencies:**
- None (first issue in P1)

**Data impact:**
- Nenhum (FE não persiste dados client-side além de auth-session storage)

**API / Contract impact:**
- Depends on `@neuroassist/shared/feature-flags` FeatureFlagMap type (must be stable before implementation)

**Testing / Evidence required:**
- Unit spec passing with the threshold invariant assertion
- `nx lint` clean (no nx-boundary violations)

**Risks / Notes:**
- `stalenessThresholdMs` and `offlineWindowMs` defaults must match the BE `stalenessThresholdSeconds=90s`
  window (api-contract §5); if BE changes the default, this must be updated together with INT-FE-EDGE-006.
- Runtime override layer for feature flags is a shared concern — confirm interface exists in
  `@neuroassist/shared/feature-flags` before implementation.
