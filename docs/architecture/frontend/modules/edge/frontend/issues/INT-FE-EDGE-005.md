## [INT-FE-EDGE-005] Shared contracts — DispositivoEdgeView, RegisterDeviceResult, LivenessView in @neuroassist/shared/contracts

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Confirms that all EDGE wire DTOs (`DispositivoEdgeView`, `RegisterDeviceResult`, `LivenessView`,
`PairingState`, `WireStatus`, `DerivedState`, `ConnectivityTone`, `DeviceConnectivityVM`, `PairingVM`,
`PairingPhase`, `DeviceDetailVM`, `PairedDeviceOutcome`) are properly exported from the
`@neuroassist/shared/contracts` public API and importable from every `scope:edge` lib without a circular dep.

**Context / Problem statement:**
- FE-EDGE-005 creates the type files; this integration issue verifies the barrel export and validates
  that no circular dependency exists between `scope:edge` and `scope:shared/contracts`.
- This is the integration gate for the shared contracts: if the barrel is wrong, every facade and
  component that imports these types will fail to build.
- Also verifies that the `@neuroassist/shared/contracts` package.json `exports` field correctly resolves
  the edge sub-path (if sub-path exports are used).

**Approach (execution path):**
1. In `libs/shared/contracts/src/index.ts`: confirm all EDGE types are re-exported.
2. Run `nx build shared-contracts` and confirm zero TS errors.
3. In a temporary test file (or in `edge-data-access` spec), import all EDGE types from
   `@neuroassist/shared/contracts` and confirm they resolve (no "has no exported member" error).
4. Run `nx graph --file=dep-graph.json` and confirm no circular dependency between `shared-contracts`
   and `edge-*` libs.

**Non-goals:**
- Does not implement the type files (FE-EDGE-005).
- Does not test runtime behavior (types are compile-time only).

**Layer(s):**
frontend / libs/shared/contracts

**Bounded context:**
shared

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-03-view-models.md` (§1–§6)
- `docs/architecture/frontend/modules/edge/frontend/contracts/backend-api-cross-link.md` (§1)

**Architecture alignment:**
- Tag Nx: `scope:shared`, `type:domain`

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/shared/contracts/src/index.ts` (confirm EDGE type exports)

**Tests**
- 🔲 `nx build shared-contracts` PASS (zero TS errors)
- 🔲 `nx build edge-data-access` PASS (imports from shared/contracts resolve)
- 🔲 `nx graph` no circular dependency between shared-contracts and edge-*

**Milestone:**
Wave P4 / Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:shared
priority:P4

**Acceptance criteria:**
- `npx nx build shared-contracts` PASS
- `npx nx build edge-data-access` PASS
- No circular dep: `nx graph` shows `shared-contracts → (no edge-* imports)`
- All 12 EDGE type names importable from `@neuroassist/shared/contracts`

**Dependencies:**
- FE-EDGE-005 (type file creation — must be done first)

**Data impact:**
- Nenhum

**API / Contract impact:**
- None (types only)

**Testing / Evidence required:**
- `nx build shared-contracts` clean output
- Import smoke test: `import { DispositivoEdgeView, PairingVM, DeviceConnectivityVM } from '@neuroassist/shared/contracts'` compiles

**Risks / Notes:**
- Sub-path exports (`@neuroassist/shared/contracts/edge`): if the project uses Node sub-path exports in
  package.json, confirm the `edge` sub-path is declared. Otherwise use top-level re-exports.
