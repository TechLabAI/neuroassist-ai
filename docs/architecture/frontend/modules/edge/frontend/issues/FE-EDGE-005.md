## [FE-EDGE-005] Shared contracts DTOs — DispositivoEdgeView, RegisterDeviceResult, LivenessView, PairingVM, DeviceConnectivityVM, DeviceDetailVM

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
All wire-bound and view-model TypeScript types for the EDGE module are declared in
`@neuroassist/shared/contracts` (or a sub-path `@neuroassist/shared/contracts/edge`). They are consumed by
`EdgeHttpService`, both facades, all smart components, and the shell read-model port.

**Context / Problem statement:**
- DTOs must be verbatim mirrors of the frozen BE wire shapes (api-contract.md §2.1, §4.1, §4.4) — no FE
  invented fields (SPEC-03 §1, SPEC-04 §1).
- `DerivedState` and `ConnectivityTone` are FE-only types (not wire) — they belong in the same file since
  they are shared across `edge/data-access`, `edge/ui`, and `edge/integration`.
- `serverTime` must be in `LivenessView` body (not only the header) — confirmed requirement for
  server-relative age derivation (SPEC-04 §4, contracts/backend-api-cross-link.md §5 open item).
- Bloqueia: FE-EDGE-006, FE-EDGE-007, FE-EDGE-008, FE-EDGE-009, FE-EDGE-011..FE-EDGE-018

**Approach (execution path):**
1. In `libs/shared/contracts/src/lib/edge/` create:
   - `dispositivo-edge.view.ts` — `DispositivoEdgeView`, `PairingState`, `WireStatus`
   - `register-device-result.ts` — `RegisterDeviceResult`
   - `liveness.view.ts` — `LivenessView`, `NotModified` marker type
   - `device-connectivity.vm.ts` — `DerivedState`, `ConnectivityTone`, `DeviceConnectivityVM`
   - `pairing.vm.ts` — `PairingPhase`, `PairingVM`
   - `device-detail.vm.ts` — `DeviceDetailVM`, `PairedDeviceOutcome`
2. Re-export from `libs/shared/contracts/src/index.ts`.
3. All types are `readonly` (record-style, no mutation).

**Non-goals:**
- Does not implement any service or facade logic.
- Does not add any field not present in the BE contract (no FE-invented wire fields).

**Layer(s):**
frontend / libs/shared/contracts

**Bounded context:**
edge (shared contracts sub-path)

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-03-view-models.md` (§1–§6)
- `docs/architecture/frontend/modules/edge/frontend/contracts/backend-api-cross-link.md` (§1)

**Architecture alignment:**
- Tag Nx: `scope:shared`, `type:domain`
- All fields `readonly` — no mutation in view-model shapes

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/shared/contracts/src/lib/edge/dispositivo-edge.view.ts`
- 🔲 `libs/shared/contracts/src/lib/edge/register-device-result.ts`
- 🔲 `libs/shared/contracts/src/lib/edge/liveness.view.ts`
- 🔲 `libs/shared/contracts/src/lib/edge/device-connectivity.vm.ts`
- 🔲 `libs/shared/contracts/src/lib/edge/pairing.vm.ts`
- 🔲 `libs/shared/contracts/src/lib/edge/device-detail.vm.ts`
- 🔲 `libs/shared/contracts/src/index.ts` (re-export all edge types)

**Tests**
- 🔲 Type-only: no runtime tests needed; TypeScript compiler (`nx build shared-contracts`) is the gate
- 🔲 `nx build shared-contracts` PASS (no TS errors)
- 🔲 `nx lint shared-contracts` PASS

**Milestone:**
Wave P2 / Data Access

**Labels:**
type:feature
layer:frontend
scope:shared
priority:P2

**Acceptance criteria:**
- `npx nx build shared-contracts` PASS (zero TS errors)
- `npx nx lint shared-contracts` PASS
- `DispositivoEdgeView.serverTime` is typed `string` (present in body, not only header)
- `DerivedState` union has exactly 5 members: `online | stale | offline | unknown | not-paired`

**Dependencies:**
- FE-EDGE-001 (foundation must exist to understand scope; no hard code dep)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Verbatim mirror of `api-contract.md §2.1, §4.1, §4.4` — changes to BE contract require synchronised update here

**Testing / Evidence required:**
- TypeScript compilation passes (no "property X does not exist" errors)
- Peer review: confirm `LivenessView.serverTime` matches BE api-contract §4.4 body shape

**Risks / Notes:**
- Confirm with BE that `serverTime` is in the `LivenessView` **body** (not only `X-Server-Time` header) —
  open item from contracts/backend-api-cross-link.md §5.
- `NotModified` marker type for 304 responses — agree pattern with data-access team (could be
  `null | LivenessView` or a discriminated union).
