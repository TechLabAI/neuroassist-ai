## [FE-EDGE-003] EDGE_MODULE_DESCRIPTOR — composition slots (connectivityReadModel + pairingComponent)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`edge.descriptor.ts` exports `EDGE_MODULE_DESCRIPTOR` — the single object the `scope:web` shell reads to
register EDGE: routes, permission map, feature flags, menu metadata, bootstrap, and the two composition slots
(`connectivityReadModel` and `pairingComponent`). This file is the shell-facing API of the EDGE module.

**Context / Problem statement:**
- The shell must register EDGE without importing scope:edge internals (nx-boundary, ADR-006). The descriptor
  is the public surface: slot A gives the shell a `Signal<DeviceConnectivityVM>` for the topbar; slot B
  gives the onboarding host page `DevicePairingPageComponent` via a lazy factory (SPEC-06 §1).
- `connectivityReadModel` depends on `DeviceFacade` and the injection context being active — it must be a
  factory function `(deps: EdgeReadModelDeps) => Signal<DeviceConnectivityVM>`, not a raw signal.
- `pairingComponent` is a dynamic import factory: `() => import('@neuroassist/edge').then(m => m.DevicePairingPageComponent)`.
- Bloqueia: INT-FE-EDGE-001, INT-FE-EDGE-002, INT-FE-EDGE-003, INT-FE-EDGE-004

**Approach (execution path):**
1. Create `libs/edge/shell-entry/src/lib/edge.descriptor.ts`.
2. Define `EdgeReadModelDeps` interface (injector or explicit `DeviceFacade` ref).
3. Implement `EDGE_MODULE_DESCRIPTOR` satisfying the `ModuleDescriptor` interface from
   `@neuroassist/shared/shell-contracts`.
4. `connectivityReadModel`: returns `DeviceFacade.connectivity` readonly signal when given deps.
5. `pairingComponent`: lazy import factory wrapping `DevicePairingPageComponent`.
6. Re-export from `libs/edge/shell-entry/src/index.ts`.

**Non-goals:**
- Does not wire the descriptor into the shell's `tool-descriptors.ts` (INT-FE-EDGE-004).
- Does not implement `DeviceFacade` (FE-EDGE-007).

**Layer(s):**
frontend / libs/edge/shell-entry

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-06-shell-integration.md` (§1, §2)

**Architecture alignment:**
- Standalone + OnPush + Signals
- Tag Nx: `scope:edge`, `type:app`
- nx-boundary: `edge.descriptor.ts` must not import from `scope:web` or any other `scope:*`

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/shell-entry/src/lib/edge.descriptor.ts` (EDGE_MODULE_DESCRIPTOR const)
- 🔲 `libs/edge/shell-entry/src/index.ts` (re-export EDGE_MODULE_DESCRIPTOR)

**Tests**
- 🔲 `libs/edge/shell-entry/src/lib/edge.descriptor.spec.ts` — descriptor.key = 'edge'; descriptor.menu.showInNav = false; pairingComponent factory returns a promise; connectivityReadModel is a function
- 🔲 `nx test edge-shell-entry` PASS
- 🔲 `nx lint edge-shell-entry` PASS (boundary: no scope:web imports)

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
- Descriptor exports `connectivityReadModel` as function and `pairingComponent` as lazy factory
- nx-boundary lint passes (no import of scope:web or sibling scope:*)

**Dependencies:**
- FE-EDGE-001 (MODULE_KEY, MODULE_MENU_METADATA, MODULE_PERMISSION_MAP, MODULE_FEATURE_FLAGS)
- FE-EDGE-002 (EDGE_ROUTES, EdgeModule)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Depends on `@neuroassist/shared/shell-contracts` ModuleDescriptor type (must be stable)

**Testing / Evidence required:**
- Unit test: lazy factory resolves to `DevicePairingPageComponent` (mock dynamic import)
- nx-boundary rule passes in lint output

**Risks / Notes:**
- The `ModuleDescriptor` interface may not exist yet in `@neuroassist/shared/shell-contracts` — confirm
  with Shell team or create a provisional interface in `edge.descriptor.ts` marked `TODO[shell-contracts]`.
- `EdgeReadModelDeps` must be agreed with INT-FE-EDGE-001 (EdgeShellIntegrationService).
