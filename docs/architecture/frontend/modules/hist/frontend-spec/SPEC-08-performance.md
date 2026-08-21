# SPEC-08 — Performance — HIST (`@neuroassist/hist`)

> The timeline can grow large for a busy clinic patient (vision R3). Budgets and strategies below keep TTI and scroll
> smooth on the reference dataset. Traces vision doc 04 §1/§6, doc 05 §3, doc 06 R3/A2.

## 1. Performance budgets

| Metric | Budget | Notes |
|---|---|---|
| Lazy chunk (`@neuroassist/hist`) | ≤ 120 KB gzip | measured at build; lazy-loaded on first `/historico` entry |
| Time to first row (loaded) | ≤ 1.5 s p75 (reference dataset, warm API) | skeleton within 100 ms of navigation |
| Filter tab switch → new list | ≤ 800 ms p75 | shows skeleton immediately (view transition) |
| Feedback write round-trip | ≤ 1.2 s p75 | per-item spinner; non-optimistic |
| Scroll frame budget | 60 fps (≤ 16 ms/frame) at page size N | OnPush + trackBy + optional virtual scroll |

## 2. Rendering strategy

- **`ChangeDetectionStrategy.OnPush` everywhere**; state flows through signals so change detection is precise.
- **`@for` with `track item.id`** — stable identity keeps DOM diffs minimal across pagination appends and reconciles.
- Presentational components are pure functions of inputs → no incidental re-render.

## 3. Data volume strategy

- **Pagination first (A2):** server pages of `size` (default 50, max 200 per api-contract §1). "carregar mais" appends the next page; loaded pages retained in the facade signal for the current period.
- **Virtual scroll (flag `virtualScroll`, default off):** enabled when a period exceeds a threshold (SPEC to confirm; start ~200 rows). Off for the pilot's low-to-moderate volume (A2); the design leaves room to switch on without a data-flow change.
- **Refetch on entry / period change** (doc 05 §3); no cross-navigation persistent cache in MVP; **no** stale-while-revalidate.

## 4. Network strategy

- Single paged GET per page; no N+1 client joins when the read model resolves `rotulo`/`urgencia`/`confirmadoPor` server-side (preferred A5). Client-side PAC/ACS joins are the fallback only and, if used, are batched, not per-row.
- Feedback POST is idempotent → safe single retry, no duplicate writes on a flaky connection.
- Timestamps localized on render from ISO (never re-fetched for formatting) — R6.

## 5. Perceived performance

- Skeleton rows appear within 100 ms of navigation and on every period switch so the screen never looks frozen.
- Feedback shows an immediate per-item spinner (not optimistic marking) so the action feels responsive while staying safe.
- The empty state is a first-class fast path (no spinner-then-empty flicker): once a page returns zero items the empty state renders directly.

## Criterios de aceite (EARS)

- WHEN the caregiver first navigates to `/historico`, THE SYSTEM SHALL lazy-load the `@neuroassist/hist` chunk and render the loading skeleton within 100 ms.
- THE SYSTEM SHALL render the timeline with `OnPush` change detection and track rows by stable id so pagination appends cause minimal DOM churn.
- WHEN a period returns more results than one page, THE SYSTEM SHALL page results and append via "carregar mais" rather than fetching the entire history at once.
- WHERE the `virtualScroll` flag is enabled, THE SYSTEM SHALL virtualize the list so scroll stays within the frame budget for large periods.
- WHEN the read model resolves label, urgency, and confirmation metadata server-side, THE SYSTEM SHALL NOT perform per-row client joins.
- THE SYSTEM SHALL localize timestamps on render from the ISO value and SHALL NOT refetch records to reformat them.
