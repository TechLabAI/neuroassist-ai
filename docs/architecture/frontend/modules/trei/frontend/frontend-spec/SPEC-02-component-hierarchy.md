# SPEC-02 — Component Hierarchy — TREI (Frontend)

> Per-feature-lib component trees. All components `standalone: true`, `OnPush` mandatory
> (vision doc 01 §6). Smart components consume facades and declare the signals they read;
> presentational components take `input()`s and emit `output()`s only. No component calls
> `HttpClient` or holds a `BehaviorSubject` (doc 05 §2). This SPEC is **prose + trees** and
> carries **no** `## Criterios de aceite (EARS)` section (per command rules).

## 1. `libs/trei/shell-entry` — lazy entry (`@neuroassist/trei`, tag `type:app`)

```
TreiRootComponent (smart, route shell for the TREI subtree)
├─ reads: TrainingSessionFacade.session()        (signal<TrainingSessionVM>)
├─ renders <trei-authoring-banner/>              (INV-6 "não é o app do paciente", always on)
├─ <router-outlet/>                              (loadComponent children below)
└─ no data fetch of its own — pure subtree host + banner + breadcrumb feed
```

The barrel `index.ts` exports `TreiModule`, `TREI_ROUTES`, `trei.contract`,
`trei.permission-map`, `trei.feature-flags`, `trei.nav.config`, `trei.bootstrap`, and
`TREI_MODULE_DESCRIPTOR` (consumed at `scope:web`, SPEC-06).

## 2. `libs/trei/feature-training-session` — Tela 08 conversation (`type:feature`)

```
TrainingSessionPageComponent (smart)
├─ reads: TrainingSessionFacade.session()  → status | activeLocale | turns | hasPendingProposals
├─ reads: TrainingSessionFacade.aiDisclosed()  (badge state, ADR-017)
├─ LocaleTabsComponent (presentational)
│    input:  activeLocale: Locale, locales: Locale[]
│    output: localeChange: EventEmitter<Locale>   → facade.switchLocale()
├─ ChatConversationPanelComponent (presentational)
│    input:  turns: ConversationTurnVM[], status: SessionStatus, aiDisclosed: boolean
│    output: caregiverTurnSubmit: EventEmitter<string>   → facade.submitTurn(instrucao)
│    ├─ ChatBubbleComponent (presentational)   input: turn: ConversationTurnVM
│    ├─ AssistantThinkingIndicatorComponent    (shown WHILE status = 'assistant-thinking')
│    └─ ChatComposerComponent (presentational)  input: disabled: boolean · output: submit
│         (disabled WHILE status ∈ {assistant-thinking, error} — never lets a turn silently drop)
├─ ConversationErrorPanelComponent (presentational)
│    input:  error: AppError | null            (shown WHILE status = 'error')
│    output: retry: EventEmitter<void>          → facade.retryLastTurn()
├─ EmptyStateComponent (presentational)         (shown WHILE status = 'idle', CTA "Iniciar treinamento")
│    output: start: EventEmitter<void>          → facade.startSession()
├─ EndSessionButtonComponent (presentational)
│    output: end: EventEmitter<void>            → facade.endSession()
└─ SessionEndedNoticeComponent (presentational) (shown WHILE status = 'ended' && hasPendingProposals)
```

Signal discipline: `TrainingSessionPageComponent` reads `session()` and `aiDisclosed()`
only; it never mutates state directly — every user action is an `output()` routed to a
`TrainingSessionFacade` method.

## 3. `libs/trei/feature-proposal-review` — proposal review + approval (`type:feature`)

```
ProposalReviewPageComponent (smart)
├─ reads: ProposalReviewFacade.proposals()      (signal<ProposedNodeVM[]>, projected for active locale)
├─ reads: ProposalReviewFacade.summary()        (computed<ApprovalSummaryVM>)
├─ reads: ProposalReviewFacade.activeLocale()   (shared selector from TrainingSessionFacade, INV-3)
├─ ProposalListComponent (presentational)
│    input:  proposals: ProposedNodeVM[], activeLocale: Locale
│    ├─ ProposedNodeCardComponent (presentational)  input: node: ProposedNodeVM
│    │    ├─ DualConfirmationIndicatorComponent (presentational)
│    │    │     input: caregiverApproved: boolean, patientApproved: boolean, locale: Locale
│    │    │     (renders "✅/⏳ cuidadora" + "✅/⏳ paciente"; patient side READ-ONLY, INV-1)
│    │    ├─ output: approve: EventEmitter<{nodeId; locale}>   → facade.approveCaregiverSide()
│    │    ├─ output: edit:    EventEmitter<{nodeId; locale}>   → opens ProposalEditDialog (pre-approval only)
│    │    └─ output: discard: EventEmitter<{nodeId; locale}>   → facade.discardDraft()
│    └─ ProposalEditDialogComponent (presentational, shared ui-feedback dialog)
│         input:  draftText: string, maxLen: number
│         output: save: EventEmitter<string>    → facade.materializeOrEditProposal()
├─ ApprovalSummaryBarComponent (presentational)
│    input: summary: ApprovalSummaryVM          (awaitingCaregiver | awaitingPatient | readyBothSides)
└─ ProposalEmptyStateComponent (presentational) (no drafts for the active locale)
```

`ProposedNodeCardComponent` exposes **no** patient-side toggle — the `paciente` indicator
is display-only, enforcing INV-1 structurally (there is simply no output that could set
`confirmadoPaciente`).

## 4. `libs/trei/ui` (optional) — trei-local presentational primitives (`type:ui`)

```
TreiAuthoringBannerComponent   ("Treinamento — não é o app do paciente" + AI-generated notice, ADR-017)
ChatBubbleComponent            (assistant vs caregiver bubble, tokens only)
StatusChipComponent            (rascunho / aguardando_aprovacao / ativo — read-model of subEstado)
```

These compose **only** `@neuroassist/shared/ui-*` tokens/primitives (no ad-hoc color or
spacing; SPEC design-tokens). They may be inlined into the feature libs if not reused.

## 5. Smart/presentational split (rule recap)

- **Smart:** `TreiRootComponent`, `TrainingSessionPageComponent`, `ProposalReviewPageComponent` — inject facades, read signals, route outputs to facade methods.
- **Presentational:** everything else — `input()`/`output()` only, `OnPush`, no injected facade, no HTTP, no router.
- Every smart component **declares the signals it reads** (listed above), so a scaffold reviewer can verify no hidden state source.
