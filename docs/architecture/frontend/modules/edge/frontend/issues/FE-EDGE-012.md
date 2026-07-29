## [FE-EDGE-012] ConnectivityStatusCardComponent — derived state display (accessible, not color-only)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`ConnectivityStatusCardComponent` in `libs/edge/feature-device-detail` renders the primary connectivity
status (large honest state: `online | stale | offline | unknown | not-paired`) using the design-token tone
plus a text label and icon — never color-only. Fully accessible for users with color-vision deficiency.

**Context / Problem statement:**
- The connectivity status is safety-critical output for a healthcare context — a caregiver must never
  misread the device state (doc 01 §3, SPEC contracts/design-tokens.md).
- WCAG 1.4.1 (use of color): color alone MUST NOT be the only visual means of conveying state.
  Each tone must have a paired text label AND an icon (e.g., wifi / wifi_off / wifi-question).
- `lastSyncRelative` ("há 2 min") is rendered via `RelativeTimeComponent` (FE-EDGE-017) as a sub-element.
- `versao` displayed when not null (agent version badge).
- Bloqueia: FE-EDGE-014 (DeviceDetailPageComponent)

**Approach (execution path):**
1. Create `libs/edge/feature-device-detail/src/lib/connectivity-status-card/connectivity-status-card.component.ts`
   as `standalone: true`, `OnPush`.
2. `@Input() derivedState: DerivedState`
3. `@Input() lastSyncRelative: string`
4. `@Input() versao: string | null`
5. State → tone map (design-tokens.md): `online→ok`, `stale→warn`, `offline→er`, `unknown→muted`, `not-paired→muted`.
6. State → icon map: `online→wifi`, `stale→wifi` (amber), `offline→wifi_off`, `unknown→wifi_question`, `not-paired→wifi_off`.
7. State → pt-BR label: "Dispositivo online" / "Sem sinal recente" / "Dispositivo offline" / "Sem confirmação de conexão" / "Nenhum dispositivo pareado".
8. `aria-label` on the card root: combine tone + state text for screen readers.

**Non-goals:**
- Does not compute `derivedState` (DeviceFacade responsibility, FE-EDGE-007).
- Does not render management actions (DeviceActionsComponent, FE-EDGE-013).

**Layer(s):**
frontend / libs/edge/feature-device-detail

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2 ConnectivityStatusCardComponent)
- `docs/architecture/frontend/modules/edge/frontend/contracts/design-tokens.md`

**Architecture alignment:**
- `standalone: true`, `OnPush`, purely presentational
- Tag Nx: `scope:edge`, `type:feature`
- WCAG 1.4.1: color + text + icon (three independent channels)

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/feature-device-detail/src/lib/connectivity-status-card/connectivity-status-card.component.ts`
- 🔲 `libs/edge/feature-device-detail/src/lib/connectivity-status-card/connectivity-status-card.component.html`
- 🔲 `libs/edge/feature-device-detail/src/lib/connectivity-status-card/connectivity-status-card.component.scss`
- 🔲 `libs/edge/feature-device-detail/src/index.ts` (re-export)

**Tests**
- 🔲 Unit: each `derivedState` value produces a distinct aria-label (5 states asserted)
- 🔲 Unit: `derivedState='offline'` renders `wifi_off` icon class AND "Dispositivo offline" text AND `er` tone class
- 🔲 Unit: `derivedState='online'` does NOT render an error-tone class
- 🔲 Unit: `versao='1.2.3'` renders the badge; `versao=null` hides the badge
- 🔲 `nx test edge-feature-device-detail` PASS
- 🔲 `nx lint edge-feature-device-detail` PASS

**Milestone:**
Wave P3 / Feature Libs — Device Detail

**Labels:**
type:feature
layer:frontend
scope:edge
priority:P3

**Acceptance criteria:**
- `npx nx test edge-feature-device-detail` PASS
- `npx nx lint edge-feature-device-detail` PASS
- All 5 DerivedState values produce distinct `aria-label`, icon, and text label
- No state produces color as the sole discriminant (confirmed by DOM test: text + icon always present)

**Dependencies:**
- FE-EDGE-005 (DerivedState, ConnectivityTone types)
- FE-EDGE-017 (RelativeTimeComponent — imported for lastSyncRelative display)

**Data impact:**
- Nenhum

**API / Contract impact:**
- None (presentational)

**Testing / Evidence required:**
- `derivedState='offline'` snapshot: wifi_off icon + "Dispositivo offline" text + `er` class all present
- `aria-label` distinctness: 5 assert statements, one per state

**Risks / Notes:**
- Icon library availability: confirm `wifi_question` (unknown state) is in the project's Material icon set;
  if not, use a fallback (`help_outline` or similar) and document the substitution.
