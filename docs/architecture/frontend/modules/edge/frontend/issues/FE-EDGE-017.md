## [FE-EDGE-017] ConnectivityChipComponent + RelativeTimeComponent (libs/edge/ui) — topbar chip, tone→pt-BR label, accessible

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Two reusable presentational primitives in `libs/edge/ui`:
- `ConnectivityChipComponent` — the always-visible topbar chip showing connectivity state. Receives
  `derivedState`, `tone`, `label` as inputs; emits `open` output on click. Color + text + icon (WCAG 1.4.1).
- `RelativeTimeComponent` — renders a relative time string ("há 2 min") from an ISO 8601 timestamp.

**Context / Problem statement:**
- `ConnectivityChipComponent` is consumed by the `scope:web` Shell topbar. It lives in `scope:edge`
  (`libs/edge/ui`) and is exported via the EDGE barrel so the Shell binds it as a composition slot
  (SPEC-02 §3, SPEC-06 §2). The Shell imports it through the descriptor — never via a direct `scope:edge`
  import from shell internals.
- The chip must never claim "online" without a fresh heartbeat — it renders whatever `derivedState` it
  receives, trusting the facade's honesty invariant (ADR-005).
- `RelativeTimeComponent` is used inside `ConnectivityStatusCardComponent` (FE-EDGE-012) and potentially
  in `DeviceMetaListComponent` — it lives in `libs/edge/ui` to be shared across EDGE's feature libs.
- Bloqueia: INT-FE-EDGE-002 (Shell topbar wiring)

**Approach (execution path):**
**ConnectivityChipComponent:**
1. Create `libs/edge/ui/src/lib/connectivity-chip/connectivity-chip.component.ts` as `standalone:true, OnPush`.
2. `@Input() derivedState: DerivedState`; `@Input() tone: ConnectivityTone`; `@Input() label: string`.
3. `@Output() open = new EventEmitter<void>()`.
4. Template: icon (wifi/wifi_off/wifi_question) + label text + tone CSS class. `(click)` emits `open`.
5. `aria-label` combining tone + label; `role="button"` on clickable wrapper.
6. When `derivedState='not-paired'`: chip is quiet (muted) — still visible but subdued.

**RelativeTimeComponent:**
1. Create `libs/edge/ui/src/lib/relative-time/relative-time.component.ts` as `standalone:true, OnPush`.
2. `@Input() iso: string | null`.
3. Renders "há N min" / "há N h" / "agora" / "–" (null). Pure date math, no external library.
4. `aria-label` with the full ISO date for screen readers.

**Non-goals:**
- Does not derive `derivedState` or `tone` (DeviceFacade responsibility).
- Does not navigate on `open` output (the host shell calls `router.navigate`).

**Layer(s):**
frontend / libs/edge/ui

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§3 libs/edge/ui)

**Architecture alignment:**
- `standalone: true`, `OnPush`, purely presentational
- Tag Nx: `scope:edge`, `type:ui`
- WCAG 1.4.1: color + text + icon on chip

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/ui/src/lib/connectivity-chip/connectivity-chip.component.ts`
- 🔲 `libs/edge/ui/src/lib/connectivity-chip/connectivity-chip.component.html`
- 🔲 `libs/edge/ui/src/lib/connectivity-chip/connectivity-chip.component.scss`
- 🔲 `libs/edge/ui/src/lib/relative-time/relative-time.component.ts`
- 🔲 `libs/edge/ui/src/index.ts` (re-export both)

**Tests**
- 🔲 ConnectivityChipComponent: `derivedState='offline'` → wifi_off icon + "Dispositivo offline" text in DOM
- 🔲 ConnectivityChipComponent: `(open)` emitted on click
- 🔲 ConnectivityChipComponent: `derivedState='not-paired'` → muted class, chip still visible
- 🔲 RelativeTimeComponent: `iso` = 2 min ago → "há 2 min"
- 🔲 RelativeTimeComponent: `iso=null` → "–" (dash)
- 🔲 `nx test edge-ui` PASS
- 🔲 `nx lint edge-ui` PASS

**Milestone:**
Wave P7 / UX Polish

**Labels:**
type:feature
layer:frontend
scope:edge
priority:P7

**Acceptance criteria:**
- `npx nx test edge-ui` PASS
- `npx nx lint edge-ui` PASS
- Chip: each `derivedState` has distinct icon, label, and tone class (5 states)
- RelativeTime: null input renders fallback dash, not a crash or empty string

**Dependencies:**
- FE-EDGE-005 (DerivedState, ConnectivityTone types)

**Data impact:**
- Nenhum

**API / Contract impact:**
- None (presentational)

**Testing / Evidence required:**
- ConnectivityChip: 5-state snapshot test (icon + label + tone class for each)
- RelativeTime: boundary test (0 min, 59 min, 1 h, null)

**Risks / Notes:**
- `RelativeTimeComponent` uses `Date.now()` for relative math — in tests, mock `Date.now` to avoid
  flaky time-dependent assertions.
- Confirm pt-BR relative time format with product: "há N min" / "há N h" / "há mais de 1 dia".
