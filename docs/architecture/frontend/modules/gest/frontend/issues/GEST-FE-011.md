# GEST-FE-011 -- GestureLiveTestComponent — smart container (signals, OnPush, @Input/@Output, lifecycle teardown)

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/8 itens
**GitHub:** [#TBD]

## Outcome
`GestureLiveTestComponent` exists in `libs/gest/feature-live-test`, is `standalone: true`, `OnPush`, reads `GestureTestFacade` signals, accepts `@Input() commandDraft: ComandoDraft`, emits `@Output() testCompleted: EventEmitter<GestureTestOutcome>`, and tears down the camera on `ngOnDestroy`.

## Context / Problem statement
- This is the only smart component in GEST. All child components are presentational. The component composes `PreviewFrameComponent`, `TestStateBadgeComponent`, `CompositeStepIndicatorComponent`, `DegradedBannerComponent`, and `LiveTestControlsComponent`.
- `ngOnDestroy → facade.stopTest()` is a mandatory lifecycle invariant (camera teardown, SPEC-02 §1).
- Source: SPEC-02 §1, SPEC-03 §5.
- Bloqueia: GEST-FE-022..026 (e2e depends on this component)

## Approach (execution path)
1. Create `gesture-live-test.component.ts` with `@Component({ standalone: true, changeDetection: ChangeDetectionStrategy.OnPush })`.
2. Inject `GestureTestFacade`; read signals `state()`, `step()`, `deviceOnline()`, `degraded()`.
3. Accept `@Input() commandDraft: ComandoDraft` and pass to `facade.startTest(draft)` on "Testar agora".
4. Emit `@Output() testCompleted = new EventEmitter<GestureTestOutcome>()` when facade emits terminal state.
5. Implement `ngOnDestroy(): void { this.facade.stopTest(); }`.
6. Template wires child components via `@Input` bindings.
7. Style in `gesture-live-test.component.scss` using design tokens only.

## Non-goals
- Not responsible for state machine logic (facade does that — GEST-FE-008).
- Not responsible for camera acquisition (facade).

## Layer(s)
frontend / feature lib

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§1)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-03-view-models.md` (§5)

## Architecture alignment
- `standalone: true`, `changeDetection: OnPush`
- Reads signals, no `BehaviorSubject`
- Tag Nx: `scope:gest`, `type:feature`

## Implementation checklist (by layer/file)

**Feature lib**
- [ ] `libs/gest/feature-live-test/src/lib/gesture-live-test/gesture-live-test.component.ts`
- [ ] `libs/gest/feature-live-test/src/lib/gesture-live-test/gesture-live-test.component.html`
- [ ] `libs/gest/feature-live-test/src/lib/gesture-live-test/gesture-live-test.component.scss`

**Tests**
- [ ] `npx nx test gest-feature-live-test` PASS (unit: ngOnDestroy calls stopTest; @Output emits on recognized)
- [ ] `npx nx lint gest-feature-live-test` PASS
- [ ] `npx nx build gest-feature-live-test` PASS

## Acceptance criteria
- `ngOnDestroy()` calls `facade.stopTest()` unconditionally
- `@Output testCompleted` emits `GestureTestOutcome` when `state` reaches `recognized` or `not-recognized`
- `npx nx build gest-feature-live-test` PASS
- Screenshots of loading, idle, testing, recognized, not-recognized, camera-error states

## Dependencies
- GEST-FE-008 (GestureTestFacade)
- GEST-FE-012..016 (presentational child components)

## Empresa-context impact
Nenhum — component is stateless beyond the current test session.

## Milestone
Wave 3 / Feature libs

## Labels
type:feature
layer:frontend
scope:gest
priority:P3

## Risks / Notes
- Composite gating (two-step recognized) must be reflected in template: show `CompositeStepIndicatorComponent` and wait for both steps before emitting `testCompleted`

## Prioridade
P3
