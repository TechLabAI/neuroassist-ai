## [FE-EDGE-009] PairingCodeCardComponent — enrollment token display, TTL countdown, copy-to-clipboard

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`PairingCodeCardComponent` in `libs/edge/feature-pairing` renders the one-time enrollment token, the TTL
countdown, and a copy-to-clipboard action. It is purely presentational: no facade dependency, all state flows
in via `@Input`. The token is shown exactly once (displayed but never persisted to localStorage or URL).

**Context / Problem statement:**
- The operator must hand the enrollment token to the person running the edge agent. Clear display of the code
  and TTL countdown is safety-critical UX (SPEC-02 §1, SPEC-07 §2 enrollment-invalid error copy).
- `expiresInSeconds` drives the visual countdown — when it reaches 0, the card must visually communicate
  "expired" (the phase transition is owned by `PairingFacade` / SPEC-03 §7).
- Accessibility: the countdown must be announced by a screen reader (`aria-live="polite"` region); the copy
  action must have an `aria-label` for screen-reader users.
- No credential secret or biometric ever enters this component's template (api-contract §8, SPEC-07 §9).
- Bloqueia: FE-EDGE-011 (DevicePairingPageComponent assembles all pairing presentational components)

**Approach (execution path):**
1. Create `libs/edge/feature-pairing/src/lib/pairing-code-card/pairing-code-card.component.ts` as
   `standalone: true`, `OnPush`.
2. `@Input() enrollmentToken: string | null`
3. `@Input() expiresInSeconds: number | null`
4. `@Input() phase: PairingPhase`
5. `@Output() copy = new EventEmitter<void>()`
6. Template: large monospace code display; countdown badge; "Copiar código" button (emits `copy`).
7. When `phase === 'expired'`: show "Código expirado — gere um novo" message, hide copy action.
8. Accessibility: `aria-live="polite"` on the countdown span; `aria-label="Copiar código de pareamento"` on button.
9. Export from `libs/edge/feature-pairing/src/index.ts`.

**Non-goals:**
- Does not own TTL countdown logic (countdown signal owned by `PairingFacade.countdown`, piped in via page component).
- Does not implement clipboard access directly — emits `copy` output; the smart component calls `navigator.clipboard.writeText`.

**Layer(s):**
frontend / libs/edge/feature-pairing

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§1 PairingCodeCardComponent)

**Architecture alignment:**
- `standalone: true`, `OnPush`, pure presentational — no facade injection
- Tag Nx: `scope:edge`, `type:feature`
- Design tokens from `@neuroassist/shared/ui-tokens` (code card background, badge color)

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/feature-pairing/src/lib/pairing-code-card/pairing-code-card.component.ts`
- 🔲 `libs/edge/feature-pairing/src/lib/pairing-code-card/pairing-code-card.component.html`
- 🔲 `libs/edge/feature-pairing/src/lib/pairing-code-card/pairing-code-card.component.scss`
- 🔲 `libs/edge/feature-pairing/src/index.ts` (re-export)

**Tests**
- 🔲 Unit: `enrollmentToken` rendered in template (DOM contains the token string)
- 🔲 Unit: `expiresInSeconds=0` + `phase='expired'` → copy button hidden; "Código expirado" visible
- 🔲 Unit: `copy` output emitted on button click
- 🔲 Unit: countdown `aria-live="polite"` region present
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
- No facade injection in component (confirmed by unit test setup — no `DeviceFacade` / `PairingFacade` in providers)
- `aria-live` region present for screen-reader countdown announcement
- Token never appears in a URL or localStorage (DOM-only display)

**Dependencies:**
- FE-EDGE-005 (PairingPhase type)
- FE-EDGE-001 (design tokens from shared/ui-tokens — indirectly via SPEC-05 design-tokens contract)

**Data impact:**
- Nenhum

**API / Contract impact:**
- None (presentational — receives enrollmentToken as @Input, never fetches)

**Testing / Evidence required:**
- DOM snapshot: token string visible in monospace code element
- Expired state: copy button gone, expired message visible

**Risks / Notes:**
- `navigator.clipboard.writeText` requires a secure context (HTTPS or localhost) — the smart component
  must handle a rejected promise gracefully (fallback: `document.execCommand('copy')` or a toast "Copie
  manualmente").
