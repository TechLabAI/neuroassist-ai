## [INT-FE-EDGE-003] Onboarding step-3 host page pairing slot (scope:web) — wire [pacienteHint] + (paired) via resolvePairingSlot

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
The `scope:web` onboarding step-3 host page (`OnboardingDispositvoPageComponent`) renders
`DevicePairingPageComponent` via `EdgeShellIntegrationService.resolvePairingSlot()`, wires
`[pacienteHint]` from the onboarding state, and handles the `(paired)` output to advance the
onboarding flow. No breadcrumb is contributed by EDGE in this composed context.

**Context / Problem statement:**
- The prototype `onboarding.component.ts` step 3 fakes pairing with a `setTimeout` — this integration
  issue replaces that fake with the real `DevicePairingPageComponent` (SPEC-06 §5).
- The composition is host-side: onboarding calls `resolvePairingSlot()` and uses `NgComponentOutlet`
  or a structural directive to render the component with the correct `pacienteHint` input.
- `(paired)` output: when EDGE emits `PairedDeviceOutcome`, onboarding advances to step 4 and may
  display a success summary ("Dispositivo pareado · online").
- No breadcrumb from EDGE: the onboarding breadcrumb trail is owned by the host page (SPEC-06 §3).

**Approach (execution path):**
1. In onboarding step-3 host page component:
   - Call `await EdgeShellIntegrationService.resolvePairingSlot()` in `ngOnInit`.
   - Use `NgComponentOutlet` with `[ngComponentOutlet]="pairingComponent"` and
     `[ngComponentOutletInputs]="{ pacienteHint: onboarding.pacienteId() }"`.
2. Handle `(paired)` output: `onboarding.onDevicePaired(outcome)` → advance step.
3. Verify: `EdgeShellIntegrationService` does NOT contribute a breadcrumb in this context
   (suppression via `libs/edge/navigation`, FE-EDGE-016).
4. Ensure `ngOnDestroy` calls `EdgeShellIntegrationService.stopConnectivity()` is NOT done by
   the host page — that is the module bootstrap's responsibility.

**Non-goals:**
- Does not implement the onboarding flow beyond step 3 slot.
- Does not test the complete onboarding flow (ACS/PAC concern).

**Layer(s):**
frontend / apps/web (scope:web onboarding step-3 host page)

**Bounded context:**
edge + web (onboarding)

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-06-shell-integration.md` (§3, §5)

**Architecture alignment:**
- Tag Nx: `scope:web`, `type:app`
- nx-boundary: no direct `scope:edge` import beyond `@neuroassist/edge` barrel

**Implementation checklist (by file):**

**App path**
- 🔲 `apps/web/src/app/onboarding/onboarding-dispositivo-page.component.ts` (call resolvePairingSlot, NgComponentOutlet)
- 🔲 `apps/web/src/app/onboarding/onboarding-dispositivo-page.component.html` (NgComponentOutlet binding)

**Tests**
- 🔲 Unit: `resolvePairingSlot()` resolves and component is rendered via `NgComponentOutlet`
- 🔲 Unit: `[pacienteHint]` bound from `onboarding.pacienteId()` signal
- 🔲 Unit: `(paired)` output calls `onboarding.onDevicePaired(outcome)`
- 🔲 `nx lint web` PASS (no boundary violation)

**Milestone:**
Wave P4 / Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:edge
priority:P4

**Acceptance criteria:**
- `npx nx lint web` PASS
- `npx nx test web` PASS (onboarding step-3 tests)
- `pacienteHint` binding confirmed (unit test asserts input value matches onboarding patient ID)
- `(paired)` output advances onboarding step (spy asserted)

**Dependencies:**
- INT-FE-EDGE-001 (EdgeShellIntegrationService.resolvePairingSlot)
- FE-EDGE-011 (DevicePairingPageComponent — the component rendered)
- FE-EDGE-016 (edge/navigation — breadcrumb suppression)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Indirectly via PairingFacade (register, pairToPatient, liveness poll)

**Testing / Evidence required:**
- `NgComponentOutlet` renders correctly with `pacienteHint` input (DOM assertion)
- `(paired)` spy called with `PairedDeviceOutcome` shape

**Risks / Notes:**
- `NgComponentOutlet` with dynamic inputs requires Angular 16+ (`ngComponentOutletInputs`). Confirm
  Angular version in the project; if older, use a wrapper component approach instead.
- Breadcrumb suppression: verify `EdgeShellIntegrationService` does not push any breadcrumb when
  no `/edge/*` route is active (covered by FE-EDGE-016 unit, verified here in integration context).
