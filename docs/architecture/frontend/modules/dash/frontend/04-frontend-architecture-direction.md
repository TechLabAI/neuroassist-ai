# 04 — Frontend Architecture Direction — DASH

## 1. Stack

Angular 21 · Nx 22.x · TypeScript strict · RxJS 7.8 + Signals · Jest (unit) ·
Playwright (e2e). Standalone components, OnPush everywhere, Signals for state.
No NgRx. HTTP through `@neuroassist/shared/access-http-base`.

## 2. App boundary

- Entry lib `libs/dash/shell-entry` with `importPath` **`@neuroassist/dash`** (ADR-006);
  the single `NgModule` is `dash.module.ts` (lazy entry).
- Thin host `apps/dash` whose `app.routes.ts` only does
  `loadChildren: () => import('@neuroassist/dash').then(m => m.DashModule)` — target for
  `nx serve/build dash` and the e2e web-server base.
- The **shell app** `neuroassist-ai-web` (`scope:web`) imports `@neuroassist/dash` in
  `config/module-descriptors.ts` and renders the shell chrome; DASH is `scope:web`'s
  default landing (`/` → Home/Status).

## 3. Library organization (Nx tags — nx-boundaries.md)

| Lib | `type:*` | `scope:*` |
|---|---|---|
| `libs/dash/shell-entry` | `type:app` | `scope:dash` |
| `libs/dash/data-access` | `type:data-access` | `scope:dash` |
| `libs/dash/feature-home` | `type:feature` | `scope:dash` |
| `libs/dash/feature-entrada` | `type:feature` | `scope:dash` |
| `libs/dash/feature-notifications` | `type:feature` | `scope:dash` |
| `libs/dash/access` | `type:domain` | `scope:dash` |
| `libs/dash/integration` | `type:domain` | `scope:dash` |
| `libs/dash/navigation` | `type:domain` | `scope:dash` |
| `libs/dash/ui` | `type:ui` | `scope:dash` |

`scope:dash` imports only `scope:dash` + `scope:shared`. It **never** imports
`scope:evnt`/`scope:edge`/`scope:hist`; those compositions live at `scope:web` (ADR-005).

## 4. Hub library dependencies

- `@neuroassist/shared/access-http-base` — HTTP + `X-Empresa-Id` interceptor.
- `@neuroassist/shared/access-decision` + `auth-session` — role, session, RBAC gates.
- `@neuroassist/shared/empresa-context` — tenant context.
- `@neuroassist/shared/contracts` — DTOs for the summary/notification endpoints.
- `@neuroassist/shared/error-model` — `ProblemDetail → AppError`.
- `@neuroassist/shared/ui-components` (incl. `ShellOutletRegistry`), `ui-tokens`,
  `ui-feedback` — chrome primitives, tokens, toasts/errors.

## 5. Routing strategy

- Shell → `@neuroassist/dash` via `loadChildren`; DASH is the default route of the panel.
- Sub-routes via `loadComponent`: `/` (Home/Status) · `/notificacoes` (center) ·
  `/entrada` (rendered **outside** the shell chrome, brand entry).
- Guards: `canActivate: [authGuard]` on the shell; `roleGuard` hides/blocks `Onboarding`
  nav for the cuidadora role.
- The **alarm overlay** is not a route — it is a global shell outlet (EVNT), mounted above
  the router-outlet; it causes no URL change and no breadcrumb.

## 6. State strategy

- Signals in the facades: `DashHomeFacade` holds `summary = signal<HomeSummaryVm>()` +
  `state`, exposed `.asReadonly()`; `NotificationsFacade` holds `items = signal<NotificationItemVm[]>()`.
- The **calm projection** is derived: `computed(() => !activeEventIsUnconfirmedEmergencia)`,
  where `activeEvent` is passed in from EVNT **at the shell** (input/injection token), not
  imported by `scope:dash`.
- No NgRx, no manual `BehaviorSubject`. Components are OnPush and read signals only.

## 7. Tool/module contract strategy (`dash.contract.ts`)

- `MODULE_KEY = 'dash'`; `ModuleMenuMetadata` = label "Status", icon, order (root of sidebar).
- `permissions`: `dash.view` (all authenticated); `dash.onboarding.entry` (admin/condutor).
- `feature-flags`: `dash.notificationCenter` (defaultState on), `dash.serverSummary`
  (defaultState on — ADR-005 aggregate path; off ⇒ degraded shell-composed fallback).
- `DASH_MODULE_DESCRIPTOR` exported for the Shell registry; declares the **global outlet
  region** name that EVNT registers into.
