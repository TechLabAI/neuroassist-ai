# 04 — Frontend Architecture Direction — GEST

## 1. Stack

- Angular 21, Nx 22.x, TypeScript strict.
- RxJS 7.8 (edge stream) bridged into Angular **Signals** (state).
- Jest (unit), Playwright (`apps/gest-e2e`).
- Tailwind utilities + design tokens (`@neuroassist/shared/ui-tokens`); no SCSS
  token layer.
- Browser MediaDevices API for the local webcam preview.

## 2. App boundary

Entry is a **library** (ADR-006): `libs/gest/shell-entry` with importPath
`@neuroassist/gest`, tags `scope:gest` + `type:app`. It exports the barrel:
`gest.module`, `gest.contract`, `gest.routes`, `gest.permission-map`,
`gest.feature-flags`, `gest.bootstrap`, `gest.nav.config`, and
`GEST_MODULE_DESCRIPTOR`. `apps/gest/` is a thin host
(`loadChildren: () => import('@neuroassist/gest').then(m => m.GestModule)`) used
for `nx serve/build gest` and as the e2e web-server base.

## 3. Library organization (Nx tags)

| Lib | Tags | Responsibility |
|---|---|---|
| `libs/gest/shell-entry` | `scope:gest` `type:app` | entry + descriptor |
| `libs/gest/data-access` | `scope:gest` `type:data-access` | `EdgeDetectionClient` (localhost bridge) + `GestureTestFacade` (signals) |
| `libs/gest/feature-live-test` | `scope:gest` `type:feature` | `GestureLiveTestComponent` (preview + verdict) |
| `libs/gest/access` | `scope:gest` `type:domain` | device-online guard |
| `libs/gest/integration` | `scope:gest` `type:domain` | `GestShellIntegrationService` |
| `libs/gest/navigation` | `scope:gest` `type:domain` | breadcrumb/menu state |
| `libs/gest/ui` (opt) | `scope:gest` `type:ui` | `TestStateBadge`, `PreviewFrame` |

## 4. Hub / shared library dependencies

- `@neuroassist/shared/access-http-base` — HTTP + `X-Empresa-Id` interceptor
  (command sync fallback).
- `@neuroassist/shared/empresa-context` — active tenant.
- `@neuroassist/shared/access-decision` + `auth-session` — route entitlement.
- `@neuroassist/shared/contracts` — Comando / TipoGesto / recognition DTOs.
- `@neuroassist/shared/error-model` — map `ProblemDetail` → `AppError`.
- `@neuroassist/shared/ui-*` — tokens, components, feedback toasts.

`data-access` may depend only on `shared/contracts`, `access-http-base`,
`empresa-context`, `error-model` (nx-boundaries.md).

## 5. Routing strategy & composition

- Shell → `@neuroassist/gest` via `loadChildren`; sub-views via `loadComponent`;
  `canActivate: [authGuard, gestDeviceOnlineGuard]`.
- **Cross-module composition (critical):** the live-test feature is consumed by
  PAC's `calibracao` and by onboarding. Because `scope:pac`/`scope:acs` cannot
  import `scope:gest` (hard boundary), composition happens at the **`scope:web`
  shell-page level**: the shell page that renders the command editor also renders
  `GestureLiveTestComponent`, wiring the current `Comando` draft into it via
  `@Input`/signal. GEST exposes the component through its barrel; the shell (not
  PAC) does the import. This is the production decomposition of the prototype's
  single `calibracao` screen. See ADR-006.

## 6. State strategy

- Signals-first: `GestureTestFacade` holds `private readonly _state =
  signal<GestureTestVM>()` exposed as `state = this._state.asReadonly()`.
- The edge recognition stream (RxJS Observable from `EdgeDetectionClient`) is
  bridged into the signal inside the facade; components read the signal only.
- No NgRx, no manual `BehaviorSubject` in components.
- Camera `MediaStream` is owned by the facade/service (single owner) so lifecycle
  cleanup is centralized and testable.

## 7. Module contract strategy (`gest.contract.ts`)

Fields to anticipate: `MODULE_KEY = 'gest'`; menu metadata (GEST is **not** a
top-level nav item by default — surfaced embedded, so `showInNav: false`);
permissions (`gest.live-test.run`); feature flags
(`edgeLiveBridge` — toggles real edge stream vs mock/degraded, default on).
