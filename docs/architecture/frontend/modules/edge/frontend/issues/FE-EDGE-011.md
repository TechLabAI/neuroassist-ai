## [FE-EDGE-011] DevicePairingPageComponent — smart container (register/pair/reset/retry lifecycle)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`DevicePairingPageComponent` in `libs/edge/feature-pairing` is the smart container for the pairing flow.
It reads `PairingFacade` signals and dispatches actions. It accepts `@Input() pacienteHint?: string` from the
onboarding host page and emits `@Output() paired = EventEmitter<PairedDeviceOutcome>` when pairing completes.
`stopPolling()` is called in `ngOnDestroy` to prevent timer leaks.

**Context / Problem statement:**
- The onboarding host page (`scope:web`) composes this component via the descriptor slot (SPEC-06 §3);
  the standalone route `/edge/pairing` also mounts it (SPEC-01).
- `pacienteHint` is advisory only (pre-fills the target patient); it is never treated as an authenticated
  identity (api-contract §2.1, SPEC-01 §4).
- The component must NOT flip to "paired" on the enrollment response alone — the facade enforces this
  invariant, and the page simply renders what the facade signals (SPEC-03 §7).
- Clipboard copy of the enrollment token: the page handles the `(copy)` output from `PairingCodeCardComponent`
  by calling `navigator.clipboard.writeText(token)` with a graceful fallback toast.
- Bloqueia: FE-EDGE-026 (Playwright e2e pairing-happy-path)

**Approach (execution path):**
1. Create `libs/edge/feature-pairing/src/lib/device-pairing-page/device-pairing-page.component.ts`
   as `standalone: true`, `OnPush`.
2. Inject `PairingFacade`.
3. Template assembles `PairingCodeCardComponent`, `PairingProgressComponent`, `PairingResultComponent`
   binding facade signals to their inputs.
4. Initial CTA "Registrar dispositivo" calls `PairingFacade.register(label, pacienteHint)`.
5. After claim: "Vincular ao paciente" calls `PairingFacade.pairToPatient(pacienteId)`.
6. On `phase='paired'` and `derivedState='online'`: emit `paired` output with `PairedDeviceOutcome`.
7. `(copy)` output from card: `navigator.clipboard.writeText(state().enrollmentToken)` with toast fallback.
8. `ngOnDestroy`: `PairingFacade.stopPolling()`.

**Non-goals:**
- Does not implement the physical timer countdown (computed in PairingFacade.countdown signal).
- Does not navigate — the host page or router handles navigation after `(paired)` emits.

**Layer(s):**
frontend / libs/edge/feature-pairing

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§1 DevicePairingPageComponent)
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-03-view-models.md` (§4, §6)

**Architecture alignment:**
- `standalone: true`, `OnPush`, signals read via `PairingFacade`
- Tag Nx: `scope:edge`, `type:feature`

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/feature-pairing/src/lib/device-pairing-page/device-pairing-page.component.ts`
- 🔲 `libs/edge/feature-pairing/src/lib/device-pairing-page/device-pairing-page.component.html`
- 🔲 `libs/edge/feature-pairing/src/index.ts` (re-export DevicePairingPageComponent)

**Tests**
- 🔲 Unit: `register()` called on CTA click with `pacienteHint` forwarded
- 🔲 Unit: `stopPolling()` called on `ngOnDestroy`
- 🔲 Unit: `(paired)` output emitted when phase=`paired` AND derivedState=`online`
- 🔲 Unit: clipboard `copy` output handled; navigator.clipboard.writeText called with token
- 🔲 `nx test edge-feature-pairing` PASS
- 🔲 `nx lint edge-feature-pairing` PASS

**Milestone:**
Wave P3 / Feature Libs — Pairing

**Labels:**
type:feature
layer:frontend
scope:edge
priority:P3

**Acceptance criteria:**
- `npx nx test edge-feature-pairing` PASS
- `npx nx lint edge-feature-pairing` PASS
- `stopPolling()` called on destroy (no leaked timer)
- `(paired)` emits only when genuinely online (not on enrollment token creation alone)

**Dependencies:**
- FE-EDGE-008 (PairingFacade — register, pairToPatient, stopPolling)
- FE-EDGE-009 (PairingCodeCardComponent)
- FE-EDGE-010 (PairingProgressComponent, PairingResultComponent)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Indirectly via PairingFacade: `POST /devices`, `POST .../pairings`, `GET .../liveness`

**Testing / Evidence required:**
- Timer leak test: `stopPolling()` spy called on `ngOnDestroy`
- Clipboard: `navigator.clipboard.writeText` spy called with enrollmentToken value

**Risks / Notes:**
- The `pacienteHint` → `pacienteId` flow: the component must clarify which `pacienteId` to pass to
  `pairToPatient()`. If `pacienteHint` is provided, use it; otherwise resolve from `empresaContext`. Document
  this decision in the component's JSDoc before implementation.
