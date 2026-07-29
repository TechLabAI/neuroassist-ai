# SPEC-02 — Component Hierarchy — GEST (Frontend)

> Every component is `standalone: true`, `OnPush` (vision doc 01 §6). Smart
> components consume the `GestureTestFacade` (signals); presentational components
> take `@Input`s and emit `@Output`s only.

## 1. `libs/gest/feature-live-test` — `feature-live-test`

The single user-facing feature. `GestureLiveTestComponent` is the smart
container; everything below it is presentational.

```
GestureLiveTestComponent                         [smart, standalone, OnPush]
│   reads signals:
│     facade.state()          → GestureTestVM
│     facade.step()           → 'principal' | 'secundario' | null
│     facade.deviceOnline()   → boolean
│     facade.degraded()       → boolean
│   @Input()  commandDraft: ComandoDraft        // wired by host page (scope:web)
│   @Output() testCompleted   = EventEmitter<GestureTestOutcome>
│   dispatches: facade.startTest(draft) / stopTest() / retry()
│   lifecycle: ngOnDestroy → facade.stopTest() (camera teardown invariant)
│
├── PreviewFrameComponent                        [presentational]
│     @Input() stream: MediaStream | null        // bound to <video>.srcObject
│     @Input() state: GestureTestState
│     (owns no stream lifecycle — display only)
│
├── TestStateBadgeComponent                      [presentational]
│     @Input() state: GestureTestState
│     @Input() message: string                   // pt-BR, human-language only
│     (renders idle | testing | recognized | not-recognized | camera-error)
│
├── CompositeStepIndicatorComponent              [presentational]
│     @Input() step: 'principal' | 'secundario' | null
│     @Input() composite: boolean
│     (two-step progress for composite gestures; hidden when simple)
│
├── DegradedBannerComponent                      [presentational]
│     @Input() degraded: boolean
│     @Input() reason: 'device-offline' | 'bridge-unreachable' | 'version-mismatch' | null
│     (honest degraded banner; shown iff degraded === true)
│
└── LiveTestControlsComponent                    [presentational]
      @Input()  state: GestureTestState
      @Output() start = EventEmitter<void>
      @Output() stop  = EventEmitter<void>
      @Output() retry = EventEmitter<void>
      ("Testar agora" / "Parar" / "Tentar de novo")
```

## 2. `libs/gest/ui` (optional) — `ui`

Reusable presentational primitives, no facade dependency:

```
TestStateBadgeComponent      (may be promoted here from feature if reused)
PreviewFrameComponent        (webcam <video> wrapper + aspect-ratio frame)
```

These are pure `@Input`/`@Output`; safe to import from `feature-live-test`.

## 3. `libs/gest/shell-entry` — entry (`type:app`)

No visual components. Exports the barrel (`gest.module`, `gest.routes`,
`gest.contract`, `gest.bootstrap`, `gest.descriptor`, …) and re-exports
`GestureLiveTestComponent` so the `scope:web` shell can compose it (SPEC-06).

## 4. Composition wiring (scope:web)

The host page (PAC command editor / onboarding gesture step) — living in
`scope:web`, the only scope permitted to import `scope:gest` — renders:

```
<pac-comando-editor-page>          [scope:web host page]
   <pac-comando-form ... />         (PAC's own form; owns the draft)
   <gest-live-test                  ← GestureLiveTestComponent (from @neuroassist/gest)
       [commandDraft]="form.draftSignal()"
       (testCompleted)="onVerdict($event)" />
```

PAC/ACS never import `scope:gest`; the shell page does. This is the production
decomposition of the prototype's single `calibracao` screen (ADR-005, doc 04 §5).

## 5. Signals read by each smart component (explicit)

| Component | Signals read | Writes (via facade dispatch) |
|---|---|---|
| `GestureLiveTestComponent` | `state`, `step`, `deviceOnline`, `degraded` | `startTest`, `stopTest`, `retry` |

No other component reads signals directly — all state flows down as `@Input`.
