# 05 — State and Data Flow — HIST

## 1. State topology

| State | Where | Scope | Notes |
|---|---|---|---|
| `items` (HistoryItemVm[]) | `HistoryFacade` (`libs/hist/data-access`) | hist-local | current period page(s) |
| `period` (`hoje`\|`semana`\|`mes`) | `HistoryFacade` | hist-local | view filter |
| `status` (idle/loading/loaded/empty/error) | `HistoryFacade` | hist-local | screen machine |
| `page` / `hasMore` | `HistoryFacade` | hist-local | pagination |
| `marking` per item | `HistoryFacade` (keyed) | hist-local | feedback sub-state |
| `error` (AppError) | `HistoryFacade` | hist-local | mapped from ProblemDetail |
| session / caregiver vínculo / `X-Empresa-Id` | `@neuroassist/shared/access` | global | injected, read-only here |

All HIST view state is **local to the facade**; only the auth/session/tenant context
is global and read-only from HIST's perspective.

## 2. Data access pattern

```
HistoryTimelineComponent
   │  (calls facade methods; reads screenVm signal)
   ▼
HistoryFacade  (signals: items, period, status, page, hasMore, error)
   │  RxJS at the edge only
   ▼
HistClient  (@neuroassist/shared/access-http-base)
   GET  /history?period={hoje|semana|mes}&page={n}&size={pageSize}
   POST /events/{id}/feedback   { verdict: 'real'|'falso_positivo' }   (see §open contract, doc 06)
```

- `HttpClient` lives in `HistClient`; the **facade emits signals**, components never
  subscribe to HTTP directly and never hold a `BehaviorSubject`.
- The read model returns items already resolved (label + urgency + confirmation
  metadata); the facade maps DTO → `HistoryItemVm`.

## 3. Cache strategy

- **Default: fetch on navigation.** Each entry to `/historico` (or period change)
  queries the read model fresh — history changes as new events land and as feedback
  is recorded.
- **Within a session**, the current period's loaded pages are kept in the facade
  signal so scrolling back up does not re-fetch; changing period resets to page 0.
- No cross-navigation persistent cache in the MVP; introduce one only if a SPEC
  proves a latency/volume need. **No** stale-while-revalidate in MVP.

## 4. Optimistic updates

- **None for feedback.** The verdict is safety-relevant and auditable, so the write
  is **non-optimistic**: the item shows `real`/`falso_positivo` only after the backend
  confirms. Rationale: an incorrectly optimistic "marked real" that later fails would
  corrupt the caregiver's trust in the audit trail.
- Filter tabs *may* update instantly (pure view change) while the list shows a
  loading skeleton — that is a view transition, not a data mutation.

## 5. Error handling

- `HistClient` surfaces backend `ProblemDetail`; mapped to `AppError` via
  `@neuroassist/shared/error-model`.
- **List load error** → `status: error`; render an inline error card with a retry
  action (`@neuroassist/shared/ui-feedback`), never a blank screen or an infinite
  spinner.
- **Feedback write error** → item `marking: mark-error`; inline "Não foi possível
  registrar. Tentar de novo."; the item stays `nao_avaliado`.
- **409 feedback-terminal** (already marked, e.g. clinic concurrency) → reconcile the
  item to the backend verdict and show "Já marcado por &lt;nome&gt; às &lt;hora&gt;"; no
  client override.
- **403 / vínculo lost** → route guard redirects; if mid-session, surface an access
  message rather than an empty list.

## 6. Breadcrumbs and navigation state

- `libs/hist/navigation` feeds `BreadcrumbItem[]`: `Início › Histórico` (and
  `Início › Histórico › <evento>` if the optional detail route is added).
- The active period is reflected in the URL (`?period=`) so the view is shareable and
  the Home deep-link ("Ver histórico de hoje") lands on the right filter.
- `HistShellIntegrationService` registers the "Histórico" menu entry and the lazy
  route with the Shell; the sidebar highlights "Histórico" when active (wireframe §5.2).
