# GEST-FE-016 -- LiveTestControlsComponent — Testar agora/Parar/Tentar de novo + gest.live-test.run RBAC hide

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens
**GitHub:** [#TBD]

## Outcome
`LiveTestControlsComponent` in `libs/gest/feature-live-test` renders "Testar agora" / "Parar" / "Tentar de novo" controls based on `GestureTestState`, and **hides** (not disables) the controls when the session lacks `gest.live-test.run` permission.

## Context / Problem statement
- Controls emit `@Output() start`, `@Output() stop`, `@Output() retry` events consumed by `GestureLiveTestComponent`.
- RBAC: when the user lacks `gest.live-test.run`, the "Testar agora" control is hidden to avoid implying a capability the caregiver does not have (SPEC-05 §5).
- Source: SPEC-02 §1, SPEC-05 §5.

## Approach (execution path)
1. Create `live-test-controls.component.ts` with `@Input() state: GestureTestState`, `@Output() start`, `@Output() stop`, `@Output() retry`.
2. Use `@neuroassist/shared/access-decision` to check `gest.live-test.run` permission; hide the run control if not granted.
3. State-driven control visibility: `idle` → "Testar agora"; `testing` → "Parar"; `recognized | not-recognized | camera-error` → "Tentar de novo".
4. Use design tokens for button styles; ARIA labels in pt-BR.

## Non-goals
- Not responsible for dispatching to the facade directly (parent component does that via `@Output` events).

## Layer(s)
frontend / feature lib

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-05-tool-contract.md` (§5)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§1)

## Architecture alignment
- `standalone: true`, `changeDetection: OnPush`
- HIDE (not disable) on missing permission
- Tag Nx: `scope:gest`, `type:feature`

## Implementation checklist (by layer/file)

**Feature lib**
- [ ] `libs/gest/feature-live-test/src/lib/live-test-controls/live-test-controls.component.ts`
- [ ] `libs/gest/feature-live-test/src/lib/live-test-controls/live-test-controls.component.html`

**Tests**
- [ ] `npx nx test gest-feature-live-test` PASS (all 3 states show correct control; permission hide)
- [ ] `npx nx lint gest-feature-live-test` PASS
- [ ] `npx nx build gest-feature-live-test` PASS

## Acceptance criteria
- `gest.live-test.run` missing → "Testar agora" not rendered in DOM (hidden, not disabled)
- `state = 'idle'` → "Testar agora" visible
- `state = 'testing'` → "Parar" visible
- `state = 'recognized' | 'not-recognized' | 'camera-error'` → "Tentar de novo" visible
- `npx nx build gest-feature-live-test` PASS

## Dependencies
- GEST-FE-001 (MODULE_PERMISSION_MAP keys)
- GEST-FE-011 (parent component wires @Output events)

## Empresa-context impact
Nenhum — permission check uses session context, not tenant parameter.

## Milestone
Wave 3 / Feature libs

## Labels
type:feature
layer:frontend
scope:gest
priority:P3

## Risks / Notes
- "Hide not disable" is a UX and security decision — do not change to disabled without revisiting SPEC-05 §5

## Prioridade
P3
