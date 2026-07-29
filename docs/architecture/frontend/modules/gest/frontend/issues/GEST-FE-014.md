# GEST-FE-014 -- CompositeStepIndicatorComponent — two-step progress for composite gestures, hidden when simple

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
`CompositeStepIndicatorComponent` in `libs/gest/feature-live-test` renders a two-step progress indicator for composite gestures (`primitivoPrincipal` + `primitivoSecundario`). Hidden when `composite === false`.

## Context / Problem statement
- Composite gesture commands require two primitives recognized in sequence within the time window. The caregiver needs visual feedback on which step they are completing.
- Accepts `@Input() step: 'principal' | 'secundario' | null` and `@Input() composite: boolean`.
- Source: SPEC-02 §1, SPEC-03 §4 (composite gating invariant).

## Approach (execution path)
1. Create `composite-step-indicator.component.ts` with two `@Input`s.
2. Template: `@if (composite)` wrapper; two step badges — "Gesto principal" and "Gesto secundário" — with active/done visual state driven by `step`.
3. Accessible: step sequence labeled for screen readers.
4. SCSS: horizontal stepper using design tokens.

## Non-goals
- Not responsible for composite timing logic (facade).

## Layer(s)
frontend / feature lib

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§1)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-03-view-models.md` (§4)

## Architecture alignment
- `standalone: true`, `changeDetection: OnPush`
- Tag Nx: `scope:gest`, `type:feature`

## Implementation checklist (by layer/file)

**Feature lib**
- [ ] `libs/gest/feature-live-test/src/lib/composite-step-indicator/composite-step-indicator.component.ts`
- [ ] `libs/gest/feature-live-test/src/lib/composite-step-indicator/composite-step-indicator.component.html`

**Tests**
- [ ] `npx nx test gest-feature-live-test` PASS (hidden when composite false; steps show correct active state)
- [ ] `npx nx lint gest-feature-live-test` PASS
- [ ] `npx nx build gest-feature-live-test` PASS

## Acceptance criteria
- `composite === false` → component renders nothing
- `step === 'principal'` → first step active, second pending
- `step === 'secundario'` → first step done, second active
- `npx nx build gest-feature-live-test` PASS

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
- `step === null` with `composite === false` is the simple command path — must not render

## Prioridade
P3
