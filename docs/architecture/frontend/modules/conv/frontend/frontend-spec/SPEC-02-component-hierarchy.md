# SPEC-02 — Component Hierarchy — CONV (Frontend)

> Prose spec (no EARS section — component trees per template §Onde NAO aplicar EARS).
> Every component is `standalone: true`, `changeDetection: OnPush`, signals-first (no
> NgRx). Smart components consume facades and declare the signals they read;
> presentational components receive `input()`s and emit `output()`s (Angular 21 signal
> I/O). Boundary rule (`nx-boundaries.md`): `scope:conv` imports only `scope:conv` +
> `scope:shared`; the GEST engine is composed at `scope:web`.

## 1. `libs/conv/patient-shell` — restricted shell

```
PatientShellComponent (smart, standalone, OnPush)
│  reads: signal ConvLocaleFacade.locale, signal PatientSessionFacade.status
│  provides: RouterOutlet for { arvore, confirmar }; NO sidebar, NO admin menu
├── PatientTopbarComponent (presentational)
│      input:  locale: Locale, patientName: string
│      output: localeSwitchRequested: EventEmitter<Locale>   (localeSwitched UI event)
└── <router-outlet/>   → SpeechTreePageComponent | ProposalConfirmPageComponent
```

The shell derives from the prototype `shell/patient-shell.component.ts` (re-synced this
session). It intentionally omits `sidebar.component.ts` — the caregiver sidebar is never
imported here (zero caregiver-surface leakage).

## 2. `libs/conv/feature-speech-tree` — Tela 06

```
SpeechTreePageComponent (smart, standalone, OnPush)
│  reads:  signal SpeechTreeFacade.vm (SpeechTreeVM), signal SpeechTreeFacade.selectedNodeId,
│          signal SemanticAssistFacade.result, signal TtsPlaybackFacade.vm (PlaybackVM)
│  writes: SpeechTreeFacade.selectNode(id) / activateNode(id) / back() / loadLevel(parentId)
│          SemanticAssistFacade.assist() ; TtsPlaybackFacade.speak(node) / replay()
│  input bridge: injected InputChannelPort (gesture) — provided at scope:web composition root
├── SpeechTreeLevelComponent (presentational)
│      input:  nodes: SpeechNodeVM[], selectedNodeId: string | null, locale: Locale
│      output: nodeSelected: EventEmitter<string>   (touch adapter → channelSelectionMoved)
│      output: nodeActivated: EventEmitter<string>  (nodeActivated UI event)
│      ├── SpeechNodeButtonComponent (presentational, one per node)
│      │      input:  node: SpeechNodeVM, selected: boolean
│      │      renders: pending-locale nodes disabled/esmaecido (contentStatus pending_locale)
│      │      output: selected / activated
│      └── (renders empty | loading | error level states as first-class)
├── InputChannelIndicatorComponent (presentational)          (ADR-005)
│      input:  activeChannel: 'gesture' | 'touch' | null, gestureAvailable: boolean
├── BackButtonComponent (presentational)
│      output: backRequested: EventEmitter<void>
├── SemanticAssistPanelComponent (presentational)
│      input:  result: SemanticAssistResultVM, enabled: boolean
│      output: assistTriggered / matchChosen(nodeId)
└── (embeds) TtsPlayerComponent  ← from libs/conv/feature-tts
```

Rule: `SpeechTreeLevelComponent` and `SpeechNodeButtonComponent` are **channel-agnostic**
— they emit `nodeSelected`/`nodeActivated` and never know whether a gesture or a touch
produced the event. The gesture adapter (`InputChannelPort`) calls the same
`SpeechTreeFacade.selectNode/activateNode` methods the touch outputs are wired to
(ADR-005: one selected-node model, one writer).

## 3. `libs/conv/feature-tts` — voice output

```
TtsPlayerComponent (smart, standalone, OnPush)
│  reads:  signal TtsPlaybackFacade.vm (PlaybackVM: state, source, nodeId)
│  writes: TtsPlaybackFacade.speak(node) / replay(nodeId) / stop()
├── PlaybackStatusComponent (presentational)
│      input:  state: PlaybackState, source: PlaybackSource
│      renders: speaking indicator; 'web-speech' fallback badge; 'text-only' full-bleed text
└── ReplayButtonComponent (presentational)
       output: playbackReplayRequested: EventEmitter<string>
```

`TtsPlaybackFacade` implements the strict fallback chain (ADR-007): cached audio →
Web Speech → prominent text. Web Speech and the `<audio>` element are the only non-GWY
browser APIs touched here; both are on-device.

## 4. `libs/conv/feature-proposal-confirm` — Tela 09

```
ProposalConfirmPageComponent (smart, standalone, OnPush)
│  reads:  signal ProposalConfirmFacade.vm (ProposalConfirmVM: pending, locale, status)
│  writes: ProposalConfirmFacade.loadPending() / confirm(id) / adjust(id, variant) / reject(id)
├── PendingConfirmationListComponent (presentational)
│      input:  pending: ProposalVM[], locale: Locale, status: ProposalConfirmStatus
│      output: confirmed / adjusted / rejected
│      └── ProposalCardComponent (presentational, one per pending node)
│             input:  proposal: ProposalVM
│             output: proposalConfirmed(id) / proposalAdjusted(id, variant) / proposalRejected(id)
│             ├── VariantPickerComponent (presentational — "Ajustar", pick-from-variants, NO free text)
│             │      input:  variants: string[]
│             │      output: variantChosen: EventEmitter<string>
│             └── (empty | loading | saving | saved | error states first-class)
```

The "Ajustar" flow **never** renders a free-text field (motor-control invariant, doc 03
§5) — it offers `proposal.variants` only.

## 5. `libs/conv/navigation` — tree-level / breadcrumb state (util)

- No visual component beyond a minimal `PatientBreadcrumbComponent` (presentational,
  `input: BreadcrumbItem[]`) rendered by the shell topbar — root → active category only,
  never a caregiver path (doc 05 §6).

## 6. Smart↔presentational contract summary

| Smart component | Facade(s) consumed | Signals read (explicit) |
|---|---|---|
| `PatientShellComponent` | `ConvLocaleFacade`, `PatientSessionFacade` | `locale`, `status` |
| `SpeechTreePageComponent` | `SpeechTreeFacade`, `SemanticAssistFacade`, `TtsPlaybackFacade` | `vm`, `selectedNodeId`, `result`, playback `vm` |
| `TtsPlayerComponent` | `TtsPlaybackFacade` | `vm` |
| `ProposalConfirmPageComponent` | `ProposalConfirmFacade` | `vm` |

All other components are presentational (inputs/outputs only, no facade injection).
