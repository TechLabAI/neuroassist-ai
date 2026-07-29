# 04 — Frontend Architecture Direction — EVNT

## 1. Stack

- **Angular 21**, **Nx 22.x**, **TypeScript strict**.
- **RxJS 7.8 + Signals** — RxJS for the live channel (SSE/interval streams),
  Signals for state exposed to components.
- **Jest** for unit; **Playwright** for e2e (`apps/evnt-e2e`).
- **No NgRx** — state lives in facade signals (ADR-002 shared, reserved but adopted
  in practice by GEST/EVNT).

## 2. App boundary

- **Entry is a library** (ADR-006): `libs/evnt/shell-entry`, importPath
  `@neuroassist/evnt`, tags `scope:evnt` `type:app`. Exports contract, module,
  routes, permission-map, feature-flags, bootstrap, nav.config, and
  `EVNT_MODULE_DESCRIPTOR` — plus the **`activeEvent` readonly signal** and the
  **AlarmOutlet** registration hook consumed by the Shell.
- `apps/evnt` is a **thin host**: `app.routes.ts` only
  `loadChildren: () => import('@neuroassist/evnt').then(m => m.EvntModule)`.
  Buildable/serveable target and e2e web-server base.

## 3. Library organization (Nx tags per module-pattern.md)

| Lib | tag `type:` | Responsibility |
|---|---|---|
| `libs/evnt/shell-entry` | `app` | Lazy entry + descriptor + facade re-export |
| `libs/evnt/data-access` | `data-access` | `EvntClient` (REST + live channel), `EventDeliveryFacade` (signals) |
| `libs/evnt/feature-alarm` | `feature` | `AlarmOverlayComponent` + confirm interaction |
| `libs/evnt/feature-routine` | `feature` | `RoutineNoticeComponent` (discreet notice) |
| `libs/evnt/access` | `domain` | evnt-local guard (caregiver ↔ patient link) |
| `libs/evnt/integration` | `domain` | `EvntShellIntegrationService` (AlarmOutlet registration) |
| `libs/evnt/navigation` | `domain` | breadcrumb/menu state |
| `libs/evnt/ui` (opt.) | `ui` | `UrgencyBadge`, `EventTimestamp` presentational bits |

All boundaries enforced by `@nx/enforce-module-boundaries`: `scope:evnt` may
import only `scope:evnt` + `scope:shared`.

## 4. Hub / shared library dependencies

- `@neuroassist/shared/access-http-base` — HTTP client + `X-Empresa-Id` interceptor.
- `@neuroassist/shared/access` — `AccessDecisionService` (route entitlement).
- `@neuroassist/shared/ui-tokens` — design tokens (`--color-alarm`, `success`, …).
- `@neuroassist/shared/ui-feedback` — toast/banner primitives for the routine notice
  and degraded banner.
- `@neuroassist/shared/error-model` — `ProblemDetail → AppError` mapping.
- `@neuroassist/shared/observability` — event/alarm/confirm telemetry.

## 5. Routing strategy

- Shell → `@neuroassist/evnt` via `loadChildren`; sub-routes (routine feed, event
  detail) via `loadComponent`.
- `canActivate: [authGuard, evntGuard]` — `evntGuard` requires the caregiver to be
  linked to the patient's tenant.
- **The emergency alarm is not a route.** It is a global **AlarmOutlet** rendered
  by the Shell above the router-outlet, driven by the `activeEvent` signal (ADR-005).
  This is the one deliberate deviation from "everything is a route" and the reason
  for the local ADR.

## 6. State strategy

- **Signals in the facade** — `EventDeliveryFacade` holds
  `#activeEvent = signal<Evento | null>(null)`, `#routineQueue = signal<Evento[]>([])`,
  `#deliveryMode = signal<'live'|'degraded'>('live')`, each exposed via
  `.asReadonly()`.
- The live channel (SSE or polling) feeds these signals; components are pure
  consumers (OnPush). No `BehaviorSubject`, no NgRx.
- `activeEvent` is the single source of truth consumed by the AlarmOutlet, Home and
  DASH — computed VMs derive from it.

## 7. Tool/module contract strategy

`evnt.contract.ts` declares:

- `MODULE_KEY = 'evnt'`, display metadata (menu label, icon).
- **Permissions**: `evnt.view`, `evnt.confirm` (confirm attendance),
  `evnt.routine.view`.
- **Feature flags**: `liveChannel` (SSE) on/off with `pollIntervalMs` fallback,
  `routineNotices` on/off.
- Exposed to the Shell via `EVNT_MODULE_DESCRIPTOR`; the AlarmOutlet registration is
  part of `bootstrap` (subscribe on init, teardown on destroy).
