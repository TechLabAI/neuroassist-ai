# 04 — Frontend Architecture Direction — EDGE

## 1. Stack

Angular 21 · Nx 22.x · TypeScript strict · RxJS 7.8 + **Signals** (Signals for
state, RxJS only where a stream is genuinely needed — e.g. the polling timer) ·
Jest (unit) · Playwright (e2e). No NgRx. No websocket/SSE (messaging disabled —
connectivity is REST + a polling timer).

## 2. App boundary

`apps/edge/` is a **thin host** whose `app.routes.ts` only lazy-loads the entry
library:
```ts
loadChildren: () => import('@neuroassist/edge').then(m => m.EdgeModule)
```
The entry lives in `libs/edge/shell-entry` as a **library** (importPath
`@neuroassist/edge`) per **ADR-006 shared**, so the Shell can import
`EDGE_MODULE_DESCRIPTOR` without app→app boundary violations.

## 3. Library organization (Nx tags — module-pattern / nx-boundaries)

| Lib | tags | Role |
|---|---|---|
| `libs/edge/shell-entry` | `scope:edge`, `type:app` | entry: module, contract, routes, descriptor |
| `libs/edge/access` | `scope:edge`, `type:access` | device/pairing guards |
| `libs/edge/data-access` | `scope:edge`, `type:data-access` | HTTP client + `DeviceFacade`, `PairingFacade` |
| `libs/edge/feature-pairing` | `scope:edge`, `type:feature` | pairing flow |
| `libs/edge/feature-device-detail` | `scope:edge`, `type:feature` | device detail / management |
| `libs/edge/integration` | `scope:edge`, `type:integration` | `EdgeShellIntegrationService` + connectivity read-model port |
| `libs/edge/navigation` | `scope:edge`, `type:navigation` | breadcrumbs state |
| `libs/edge/ui` (opt.) | `scope:edge`, `type:ui` | status chip, pairing-code card |

Boundary rule: `scope:edge` may depend only on `scope:edge` + `scope:shared`.
The **Shell** (`scope:web`) consumes EDGE's connectivity read-model via the
descriptor / a shared port — it never imports `scope:edge` internals.

## 4. Hub library dependencies

- `@hub/access/http-base` (or project equivalent) — gateway HTTP client.
- `@neuroassist/shared/*` — design tokens, UI primitives (status badge/chip,
  card, button — reusing the prototype's `ds-badge`/`ds-card` semantics).
- `@hub/error-model` + `@hub/shared/ui-feedback` (or project equivalents) —
  `ProblemDetail → AppError` mapping and user feedback.
- Shared access-layer contracts (`access-layer-contracts.md`) for the active
  `Tenant`/`Paciente` context and entitlement decisions.

## 5. Routing strategy

- Shell → `@neuroassist/edge` via `loadChildren`.
- Sub-routes via `loadComponent` (lazy): `/edge/pairing`, `/edge/device`.
- Guards: `canActivate: [authGuard, deviceGuard]`; pairing/unpair additionally
  gated by the conductor entitlement.
- The **onboarding step 3** pairing surface is composed by the onboarding flow at
  the Shell level, hosting EDGE's `feature-pairing` component (EDGE owns the flow;
  the Shell composes it — the reverse import never happens).

## 6. State strategy

- **Signals-first facades.** `DeviceFacade` and `PairingFacade` hold `signal<T>`
  private state exposed via `.asReadonly()`. No NgRx, no manual `BehaviorSubject`.
- **Derived connectivity** is a `computed()` over the latest polled device +
  a clock signal, applying the staleness/offline thresholds (ADR-005).
- **Polling** is the one place RxJS earns its keep: a `timer(...)` → `switchMap`
  to the status endpoint, feeding the device signal; paused when the tab is hidden
  and on navigation away, to avoid needless gateway load.
- The **connectivity read-model** exposed to the Shell is a readonly signal derived
  from `DeviceFacade`; the Shell subscribes via the integration port.

## 7. Module contract strategy (`edge.contract.ts`)

Fields anticipated:
- **menu metadata** — label "Dispositivo", icon `wifi` (topbar reuses status icon).
- **permissions** — `edge:read` (monitoring), `edge:pair` / `edge:unpair`
  (conductor only).
- **feature-flags** — `edge.pollingEnabled`, `edge.stalenessThresholdMs`,
  `edge.offlineWindowMs` (tunable without redeploy), `edge.rePairEnabled`.
- **descriptor** — `EDGE_MODULE_DESCRIPTOR` exporting the lazy route + the
  connectivity read-model provider the Shell topbar consumes.
