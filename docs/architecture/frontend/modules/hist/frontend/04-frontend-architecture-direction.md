# 04 — Frontend Architecture Direction — HIST

## 1. Stack

- **Angular 21**, standalone components, `ChangeDetectionStrategy.OnPush` everywhere.
- **Nx 22.x** monorepo; module boundaries enforced by `@nx/enforce-module-boundaries`.
- **TypeScript strict** (project default).
- **RxJS 7.8** at the HTTP edge only; **Signals** for all component/facade state.
- **Jest** for unit; **Playwright** (`apps/hist-e2e`) for e2e.
- Styling: Tailwind utilities + design tokens (`@neuroassist/shared/ui-tokens`); no SCSS token layer.

## 2. App boundary

- Entry is a **library** (ADR-006): `libs/hist/shell-entry`, `importPath`
  **`@neuroassist/hist`**, tags `scope:hist` `type:app`. It exports contract +
  module + routes + permission-map + feature-flags + bootstrap + nav.config +
  `HIST_MODULE_DESCRIPTOR`.
- `apps/hist` is a **thin host**: its `app.routes.ts` only
  `loadChildren: () => import('@neuroassist/hist').then(m => m.HistModule)` — the
  buildable/serveable target and e2e web-server base.
- The Shell (`neuroassist-ai-web`) imports `@neuroassist/hist` in
  `config/module-descriptors.ts` (boundary `scope:web → scope:hist` allowed).

## 3. Library organization (Nx tags)

| Lib | Nx tags | Responsibility |
|---|---|---|
| `libs/hist/shell-entry` | `scope:hist, type:app` | lazy `NgModule` entry + descriptor |
| `libs/hist/data-access` | `scope:hist, type:data-access` | `HistClient` (REST) + `HistoryFacade` (signals) |
| `libs/hist/feature-timeline` | `scope:hist, type:feature` | `HistoryTimelineComponent` (list + filter + feedback) |
| `libs/hist/access` | `scope:hist, type:domain` | hist-local guard (caregiver↔patient vínculo) |
| `libs/hist/integration` | `scope:hist, type:domain` | `HistShellIntegrationService` (menu/route registration) |
| `libs/hist/navigation` | `scope:hist, type:domain` | breadcrumb / menu state |
| `libs/hist/ui` (optional) | `scope:hist, type:ui` | `EventListItem`, `UrgencyBadge`, `FilterTabs`, `EmptyState`, `ListSkeleton` |
| `libs/hist/styles` (optional) | `scope:hist, type:ui` | Tailwind utilities + tokens |

**Boundary rule:** `scope:hist` may depend only on `scope:hist` + `scope:shared`.
No `scope:hist → scope:evnt` (or any sibling module). Shared UI treatments (urgency
badge palette) live in `@neuroassist/shared`.

## 4. Hub / shared library dependencies

- `@neuroassist/shared/access-http-base` — `HttpClient` base, `X-Empresa-Id`
  interceptor, auth token attach.
- `@neuroassist/shared/access` — `AccessDecisionService` (entitlement) + session/vínculo.
- `@neuroassist/shared/error-model` — `ProblemDetail` → `AppError` mapping.
- `@neuroassist/shared/ui-feedback` — inline error / retry / toast surfaces.
- `@neuroassist/shared/ui-tokens` — design tokens (urgency colors, typography, spacing).
- `@neuroassist/shared/observability` — event/error emission.
- `@neuroassist/shared/ui` (if present) — DS primitives (buttons, tabs, skeleton).

## 5. Routing strategy

- Shell → `@neuroassist/hist` via `loadChildren` (lazy).
- Sub-routes via `loadComponent`:
  - `/historico` → `HistoryTimelineComponent` (default; honors `?period=` query param).
  - (optional post-MVP) `/historico/:eventId` → event detail (`loadComponent`).
- Guards: `canActivate: [authGuard, histGuard]` — `authGuard` (shared) +
  `histGuard` (caregiver linked to the patient's tenant).
- The Home "Ver histórico de hoje" link deep-links to `/historico?period=hoje`.

## 6. State strategy

- **Signals in the facade** (`HistoryFacade`): `private signal<T>` + public
  `.asReadonly()`. Core signals: `items`, `period`, `status`, `page`, `hasMore`,
  `error`; a `computed` `screenVm` assembles `HistoryScreenVm`. Per-item marking
  state tracked in a keyed signal/map.
- **No NgRx**, no manual `BehaviorSubject` (ADR-002 shared, reserved but honored as
  the project standard).
- RxJS only at the `HistClient` HTTP edge; the facade converts to signals.
- Non-optimistic feedback write: item updates only on backend success.

## 7. Tool contract strategy (`hist.contract.ts`)

Fields anticipated in the module contract:

- `MODULE_KEY = 'hist'`
- `metadata`: `{ title: 'Histórico', icon, order }` (nav label "Histórico" per wireframe sidebar).
- `permissions`: `hist.read` (view history), `hist.feedback` (mark real/falso-positivo).
- `featureFlags`: `defaultPeriod ('hoje')`, `pageSize`, `virtualScroll (on/off)`,
  `feedbackTarget ('evnt' | 'hist')` — see doc 06 open contract.
- `routes`: `HIST_ROUTES` (timeline; optional detail).
- `descriptor`: `HIST_MODULE_DESCRIPTOR` (menu entry + lazy route, consumed by Shell).
