# SPEC-03 — State Contracts — HIST (`@neuroassist/hist`)

> Signals-first. All view state is **local to `HistoryFacade`** (`libs/hist/data-access`); only auth/session/tenant
> is global and read-only from HIST's perspective. Traces vision doc 05 (state topology). No NgRx, no `BehaviorSubject`.

## 1. Domain types

```ts
type Period       = 'hoje' | 'semana' | 'mes';
type Verdict      = 'real' | 'falso_positivo';
type Urgency      = 'rotina' | 'atencao' | 'critico';
type ScreenStatus = 'idle' | 'loading' | 'loaded' | 'empty' | 'error';
type MarkingState = 'idle' | 'marking' | 'mark-error' | 'marked';
```

## 2. View-model records

```ts
/** One timeline row — DTO (AuditRecordResponse) mapped to a human-readable VM. */
export interface HistoryItemVm {
  readonly id: string;                 // HIST record id (AuditRecordResponse.id)
  readonly factType: string;           // e.g. 'evento.confirmado'
  readonly label: string;              // Comando.rotulo — human-readable, NEVER a technical code
  readonly urgency: Urgency;           // resolved by read model (PAC), token-driven badge
  readonly occurredAtLabel: string;    // localized "14h32" from occurredAt (tenant locale)
  readonly recordedAtIso: string;      // authoritative order key (recordedAt)
  readonly confirmationLine: string;   // "Você confirmou às 14h33" (from actor.displayNameSnapshot + payload)
  readonly verdict: Verdict | null;    // terminal feedback verdict; null = nao_avaliado
  readonly correctsRecordId: string | null; // links a corrective record to its original (ADR-004)
}

/** Per-item feedback sub-state, keyed by item id. */
export interface ItemMarking {
  readonly id: string;
  readonly state: MarkingState;
  readonly reconciledBy?: string;      // "Ana Costa" — set on 409 terminal reconcile
  readonly reconciledAtLabel?: string; // "às 14h33"
}

/** Screen view-model consumed by HistoryTimelineComponent. */
export interface HistoryScreenVm {
  readonly items: readonly HistoryItemVm[];
  readonly period: Period;
  readonly status: ScreenStatus;
  readonly hasMore: boolean;
  readonly error: AppError | null;     // @neuroassist/shared/error-model
}
```

## 3. Facade signal shape (`HistoryFacade`)

| Signal | Type | Initial | Mutated by |
|---|---|---|---|
| `#items` | `signal<HistoryItemVm[]>` | `[]` | `load`, `loadMore`, `markFeedback` (single-item reconcile) |
| `#period` | `signal<Period>` | `defaultPeriod` flag | `setPeriod` |
| `#status` | `signal<ScreenStatus>` | `'idle'` | `load`, `loadMore` |
| `#page` | `signal<number>` | `0` | `load` (reset), `loadMore` (+1) |
| `#hasMore` | `signal<boolean>` | `false` | derived from page `total` (api-contract §1 pagination) |
| `#error` | `signal<AppError \| null>` | `null` | error mapping |
| `#markings` | `signal<Map<string, ItemMarking>>` | empty | `markFeedback` |
| `screenVm` | `computed<HistoryScreenVm>` | — | assembles the above |

Public API exposes `.asReadonly()` signals plus the computed `screenVm`; the private writable signals are never handed to components.

## 4. Facade methods (UI events)

| Method | Trigger | Effect |
|---|---|---|
| `load(period)` | route entry / retry | `status=loading`; `page=0`; GET history; `status=loaded\|empty\|error` |
| `setPeriod(period)` | `FilterTabsComponent.periodChange` | update URL `?period=`; reset page 0; `load(period)` |
| `loadMore()` | `LoadMoreComponent.loadMore` | `page+1`; GET next page; append to `#items` |
| `markFeedback(id, verdict)` | `FeedbackActionComponent.mark` | `marking=marking`; **non-optimistic** POST; on success `marking=marked` + set item verdict; on error/409 per SPEC-06 |
| `retry()` | `InlineErrorComponent.retry` | re-run last `load(period)` |

## 5. Pagination & period mapping

- Facade `period` → backend `from`/`to` timestamps computed in the tenant locale (`hoje` = start-of-day..now; `semana` = last 7 days; `mes` = last 30 days). The backend query is `recordedAt`-bounded (api-contract §3.1); there is **no** literal `period` param at the HTTP edge — mapping is the facade's job.
- `hasMore = (page + 1) * size < total` from the paged response `{ items, page, size, total }`.
- Loaded pages for the current period are retained in `#items` so scrolling back does not refetch; `setPeriod` resets to page 0 (doc 05 §3).

## Criterios de aceite (EARS)

- WHILE `status` is `loading`, THE SYSTEM SHALL render the list skeleton and SHALL NOT render item rows or an empty state.
- WHEN a history page returns zero items for the active period, THE SYSTEM SHALL set `status` to `empty` and render the reassuring empty state.
- WHEN a history page returns one or more items, THE SYSTEM SHALL set `status` to `loaded` and render the rows ordered by `recordedAt` descending.
- THE SYSTEM SHALL render each item's `label` as the human-readable `Comando.rotulo` and SHALL NOT display the technical `factType` code as the primary label.
- WHEN `setPeriod` changes the active period, THE SYSTEM SHALL reset pagination to page 0 and reload the timeline for the new period.
- WHILE a page of results does not exhaust `total`, THE SYSTEM SHALL keep `hasMore` true so the "carregar mais" affordance is available.
- WHERE an item carries `correctsRecordId`, THE SYSTEM SHALL render the corrective record linked to its original and keep both visible (ADR-004).
- WHILE an item's `marking` is `marking`, THE SYSTEM SHALL show a per-item progress indicator and SHALL keep the item verdict unset until the backend confirms (non-optimistic).
