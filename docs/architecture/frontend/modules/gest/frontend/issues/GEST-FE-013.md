# GEST-FE-013 -- TestStateBadgeComponent — 5-state badge + pt-BR messages

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
`TestStateBadgeComponent` in `libs/gest/ui` renders a visual badge for one of 5 states (`idle | testing | recognized | not-recognized | camera-error`) with a pt-BR human-language message. No raw confidence or threshold numbers ever displayed.

## Context / Problem statement
- Presentational: accepts `@Input() state: GestureTestState` and `@Input() message: string` (pt-BR, already mapped in facade).
- Used by `GestureLiveTestComponent` to show the current test verdict to the caregiver.
- Source: SPEC-02 §1, SPEC-03 §4 (no raw numbers invariant).

## Approach (execution path)
1. Create `test-state-badge.component.ts` with two `@Input`s.
2. Template maps state to icon + color (design tokens: success/error/neutral/warning).
3. ARIA role `status` for live region accessibility.
4. No conditional logic beyond state → visual variant.

## Non-goals
- Not responsible for the degraded banner (DegradedBannerComponent — GEST-FE-015).

## Layer(s)
frontend / ui lib

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§1)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-03-view-models.md` (§4)

## Architecture alignment
- `standalone: true`, `changeDetection: OnPush`
- ARIA `role="status"` for a11y
- Tag Nx: `scope:gest`, `type:ui`

## Implementation checklist (by layer/file)

**UI lib**
- [ ] `libs/gest/ui/src/lib/test-state-badge/test-state-badge.component.ts`
- [ ] `libs/gest/ui/src/lib/test-state-badge/test-state-badge.component.html`

**Tests**
- [ ] `npx nx test gest-ui` PASS (all 5 state variants render; ARIA role present)
- [ ] `npx nx lint gest-ui` PASS
- [ ] `npx nx build gest-ui` PASS

## Acceptance criteria
- All 5 `GestureTestState` values render without errors
- `message` input displays pt-BR text only (no numbers)
- ARIA `role="status"` present
- `npx nx build gest-ui` PASS

## Dependencies
- GEST-FE-011 (consumed by GestureLiveTestComponent)

## Empresa-context impact
Nenhum.

## Milestone
Wave 3 / Feature libs

## Labels
type:feature
layer:frontend
scope:gest
priority:P3

## Risks / Notes
- `message` input is the only text shown — the facade must never put raw confidence in it

## Prioridade
P3
