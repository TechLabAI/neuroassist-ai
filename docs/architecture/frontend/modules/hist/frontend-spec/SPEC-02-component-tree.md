# SPEC-02 — Component Tree — HIST (`@neuroassist/hist`)

> Standalone components, `ChangeDetectionStrategy.OnPush`, signals-first (no NgRx). Smart components consume
> the `HistoryFacade`; presentational components receive `input()`s and emit `output()`s. Traces vision doc 02 §4, doc 04 §3.

## 1. Library map (Nx tags)

| Lib | Nx tags | Contains |
|---|---|---|
| `libs/hist/shell-entry` | `scope:hist, type:app` | `HistModule`, `hist.contract.ts`, `hist.routes.ts`, `hist.bootstrap.ts`, `hist.descriptor.ts` |
| `libs/hist/data-access` | `scope:hist, type:data-access` | `HistClient` (REST), `HistoryFacade` (signals) |
| `libs/hist/feature-timeline` | `scope:hist, type:feature` | `HistoryTimelineComponent` (smart), `EventDetailComponent` (post-MVP) |
| `libs/hist/access` | `scope:hist, type:domain` | `histGuard` |
| `libs/hist/integration` | `scope:hist, type:domain` | `HistShellIntegrationService` |
| `libs/hist/navigation` | `scope:hist, type:domain` | breadcrumb/menu state |
| `libs/hist/ui` (optional) | `scope:hist, type:ui` | presentational primitives below |

**Boundary rule:** `scope:hist` depends only on `scope:hist` + `scope:shared`. No `scope:hist → scope:evnt`.

## 2. feature-timeline — component tree

```
HistoryTimelineComponent            (smart, OnPush)  [libs/hist/feature-timeline]
│  reads signals: screenVm (computed), period, status, hasMore, error
│  injects: HistoryFacade
│  calls: facade.load(period) · facade.setPeriod(p) · facade.loadMore() · facade.markFeedback(id, verdict) · facade.retry()
│
├── FilterTabsComponent            (presentational)  [libs/hist/ui]
│      input:  period: Period
│      output: periodChange: EventEmitter<Period>
│
├── @if status === 'loading'  → ListSkeletonComponent   (presentational)
│
├── @if status === 'error'    → InlineErrorComponent    (@neuroassist/shared/ui-feedback)
│      input:  error: AppError
│      output: retry: EventEmitter<void>
│
├── @if status === 'empty'    → EmptyStateComponent     (presentational) [libs/hist/ui]
│      input:  period: Period   ("Nenhum evento neste período — ótimo sinal")
│
└── @if status === 'loaded'   → list (semantic <ul role="list">)
       └── @for item of screenVm.items  →  EventListItemComponent  (presentational) [libs/hist/ui]
              input:  item: HistoryItemVm
              input:  marking: MarkingState
              output: markFeedback: EventEmitter<{ id: string; verdict: Verdict }>
              output: openDetail: EventEmitter<string>   (post-MVP)
              │
              ├── UrgencyBadgeComponent   (presentational, token-driven) [libs/hist/ui]
              │      input: urgency: Urgency
              └── FeedbackActionComponent (presentational) [libs/hist/ui]
                     input:  marking: MarkingState
                     input:  verdict: Verdict | null   (terminal display)
                     output: mark: EventEmitter<Verdict>
       └── @if hasMore  →  LoadMoreComponent   (presentational)
              output: loadMore: EventEmitter<void>
```

## 3. Smart component signal reads

`HistoryTimelineComponent` (the only smart component) reads, exclusively from `HistoryFacade`:

| Signal | Type | Use |
|---|---|---|
| `screenVm` | `computed<HistoryScreenVm>` | Assembled VM (items + period + status + hasMore + error) bound to the template. |
| `status` | `Signal<ScreenStatus>` | Drives the `@if` branch (loading/error/empty/loaded). |
| `period` | `Signal<Period>` | Highlights the active `FilterTabsComponent` tab. |
| `hasMore` | `Signal<boolean>` | Shows/hides `LoadMoreComponent`. |
| `markingFor(id)` | `Signal<MarkingState>` | Per-item feedback sub-state (keyed). |

The component holds **no** local mutable list state; every mutation goes through a facade method that updates the facade signals.

## 4. Presentational contracts (inputs/outputs)

| Component | Inputs | Outputs |
|---|---|---|
| `FilterTabsComponent` | `period` | `periodChange` |
| `EventListItemComponent` | `item`, `marking` | `markFeedback`, `openDetail` |
| `UrgencyBadgeComponent` | `urgency` | — |
| `FeedbackActionComponent` | `marking`, `verdict` | `mark` |
| `EmptyStateComponent` | `period` | — |
| `ListSkeletonComponent` | `rows?` | — |
| `LoadMoreComponent` | `busy?` | `loadMore` |

Rationale (prose): presentational primitives are pure functions of their inputs so they are trivially unit-testable and reusable; the smart `HistoryTimelineComponent` is the single point that touches the facade, keeping the data-flow one-directional (facade signals → template → user events → facade methods).
