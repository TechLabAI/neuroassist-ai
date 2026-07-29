## [FE-EDGE-010] PairingProgressComponent + PairingResultComponent — step indicator and outcome states

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Two small presentational components in `libs/edge/feature-pairing`:
- `PairingProgressComponent` — 4-step ordered indicator (registering → awaiting-claim → awaiting-first-heartbeat → paired).
- `PairingResultComponent` — renders the outcome state (paired·online·version X / expired / error) and emits `retry` output.

**Context / Problem statement:**
- The progress indicator gives the operator clear visual feedback on which pairing step is active. It mirrors
  the prototype onboarding checklist but is made honest: it reflects `PairingPhase` (not a fake setTimeout).
- `PairingResultComponent` is shown when `phase ∈ {paired, expired, error}`. It shows agent version on success
  and a re-register CTA on expired/error.
- `derivedState` in `PairingResultComponent` is derived from `PairingVM.derivedState` — shows "pareado · online"
  only when the device is genuinely fresh (ADR-005 no-premature-paired).
- Bloqueia: FE-EDGE-011 (DevicePairingPageComponent)

**Approach (execution path):**
1. `PairingProgressComponent`:
   - `@Input() phase: PairingPhase`
   - Render steps as an ordered list; highlight current step; mark completed steps.
   - Steps: "Registrando dispositivo" / "Aguardando código" / "Aguardando primeiro heartbeat" / "Pareado".
   - Accessibility: `aria-current="step"` on active step.
2. `PairingResultComponent`:
   - `@Input() phase: PairingPhase`
   - `@Input() versao: string | null`
   - `@Input() derivedState: DerivedState`
   - `@Output() retry = new EventEmitter<void>()`
   - `phase='paired' + derivedState='online'`: "Dispositivo pareado · online · versão X"
   - `phase='expired'`: "Código expirado — gere um novo" + retry CTA
   - `phase='error'`: "Erro ao parear — tente novamente" + retry CTA
3. Export both from `libs/edge/feature-pairing/src/index.ts`.

**Non-goals:**
- Does not manage phase transitions (PairingFacade).
- Does not show the enrollment token (PairingCodeCardComponent, FE-EDGE-009).

**Layer(s):**
frontend / libs/edge/feature-pairing

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§1 PairingProgressComponent, PairingResultComponent)

**Architecture alignment:**
- `standalone: true`, `OnPush`, purely presentational
- Tag Nx: `scope:edge`, `type:feature`

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/feature-pairing/src/lib/pairing-progress/pairing-progress.component.ts`
- 🔲 `libs/edge/feature-pairing/src/lib/pairing-progress/pairing-progress.component.html`
- 🔲 `libs/edge/feature-pairing/src/lib/pairing-result/pairing-result.component.ts`
- 🔲 `libs/edge/feature-pairing/src/lib/pairing-result/pairing-result.component.html`
- 🔲 `libs/edge/feature-pairing/src/index.ts` (re-export both)

**Tests**
- 🔲 PairingProgressComponent: phase=`awaiting-claim` → step 2 is `aria-current="step"`; steps 1 completed
- 🔲 PairingResultComponent: phase=`paired` + `derivedState='online'` → "pareado · online" text visible + versão shown
- 🔲 PairingResultComponent: phase=`expired` → retry CTA visible
- 🔲 PairingResultComponent: `retry` emitted on CTA click
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
- Result component: "pareado · online" only when `derivedState='online'` (not on `paired+unknown`)
- `aria-current="step"` on active progress step

**Dependencies:**
- FE-EDGE-005 (PairingPhase, DerivedState types)

**Data impact:**
- Nenhum

**API / Contract impact:**
- None (presentational)

**Testing / Evidence required:**
- `derivedState='unknown'` does NOT render "online" text in result component

**Risks / Notes:**
- Step labels must be in pt-BR; confirm approved copy ("Aguardando código" etc.) with product before
  this issue ships.
