## [INT-FE-EDGE-001] EdgeShellIntegrationService — registerWithShell, connectivityReadModel, resolvePairingSlot

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`EdgeShellIntegrationService` in `libs/edge/integration` is the single facade the `scope:web` shell calls
to consume EDGE's two composition slots: `connectivityReadModel()` (topbar) and `resolvePairingSlot()`
(onboarding step 3). It publishes the descriptor and manages the breadcrumb suppression in composed context.

**Context / Problem statement:**
- The Shell must never import `scope:edge` internals directly (nx-boundary, ADR-006).
  `EdgeShellIntegrationService` is the composition contract: it wraps `DeviceFacade.connectivity` as a
  readonly signal and returns `DevicePairingPageComponent` as a lazy `Type<>` (SPEC-06 §3).
- `registerWithShell()` is called by `edgeBootstrap.init()` and publishes `EDGE_MODULE_DESCRIPTOR` to
  the Shell's module registry.
- `startConnectivityForActivePatient()` wraps `DeviceFacade.resolveForActivePatient()` + `startPolling()`.
- Bloqueia: INT-FE-EDGE-002 (Shell topbar wiring), INT-FE-EDGE-003 (onboarding slot)

**Approach (execution path):**
1. Create `libs/edge/integration/src/lib/edge-shell-integration.service.ts`.
2. Inject `DeviceFacade`, `PairingFacade` (optional), `Router`.
3. `connectivityReadModel(): Signal<DeviceConnectivityVM>` → returns `DeviceFacade.connectivity`.
4. `resolvePairingSlot(): Promise<Type<DevicePairingPageComponent>>` → dynamic import.
5. `registerWithShell()` → publishes `EDGE_MODULE_DESCRIPTOR` to `ModuleRegistryService`.
6. `startConnectivityForActivePatient()` → `DeviceFacade.resolveForActivePatient()` + `startPolling()`.
7. `stopConnectivity()` → `DeviceFacade.stopPolling()`.
8. Breadcrumb suppression: when no `/edge/*` route is active, `BreadcrumbService.suppressScope('edge')`.
9. Export from `libs/edge/integration/src/index.ts`.

**Non-goals:**
- Does not implement `DeviceFacade` (FE-EDGE-007).
- Does not wire the topbar UI (INT-FE-EDGE-002).

**Layer(s):**
frontend / libs/edge/integration

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-06-shell-integration.md` (§3)

**Architecture alignment:**
- Tag Nx: `scope:edge`, `type:domain`

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/integration/src/lib/edge-shell-integration.service.ts`
- 🔲 `libs/edge/integration/src/index.ts` (re-export)

**Tests**
- 🔲 Unit: `connectivityReadModel()` returns the signal from `DeviceFacade.connectivity`
- 🔲 Unit: `resolvePairingSlot()` resolves to `DevicePairingPageComponent`
- 🔲 Unit: `registerWithShell()` calls `ModuleRegistryService.register(EDGE_MODULE_DESCRIPTOR)`
- 🔲 `nx test edge-integration` PASS
- 🔲 `nx lint edge-integration` PASS

**Milestone:**
Wave P4 / Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:edge
priority:P4

**Acceptance criteria:**
- `npx nx test edge-integration` PASS
- `npx nx lint edge-integration` PASS
- `connectivityReadModel()` returns `DeviceFacade.connectivity` (no re-derivation)
- nx-boundary: `edge-integration` does NOT import `scope:web` or `scope:acs`

**Dependencies:**
- FE-EDGE-003 (EDGE_MODULE_DESCRIPTOR)
- FE-EDGE-007 (DeviceFacade.connectivity)
- FE-EDGE-011 (DevicePairingPageComponent — lazy slot)

**Data impact:**
- Nenhum

**API / Contract impact:**
- None (composition layer; no HTTP calls)

**Testing / Evidence required:**
- `resolvePairingSlot()` dynamic import resolves correctly (mock dynamic import in test)

**Risks / Notes:**
- `ModuleRegistryService` in scope:web may not exist yet. Use a provisional injection token
  `MODULE_REGISTRY_TOKEN` and mock in tests.
