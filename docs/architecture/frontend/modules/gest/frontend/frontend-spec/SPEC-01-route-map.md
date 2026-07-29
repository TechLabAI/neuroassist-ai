# SPEC-01 — Route Map — GEST (Frontend)

> Module: **GEST — Detecção de Gesto (Edge)** · npm scope: `@neuroassist` · Entry lib: `@neuroassist/gest`
> Surface type: **embedded live-test feature** (no top-level nav). Composed at `scope:web` (ADR-005 / vision doc 02 §4, doc 04 §5).

## 1. Routing posture

GEST is **not** a primary navigation destination. Its only user-facing surface is
the `GestureLiveTestComponent`, which is **composed into host pages owned by other
modules** (PAC `calibração`, ACS/onboarding) at the `scope:web` shell level. GEST
therefore exposes **no standalone product route** in the shell nav.

Two route contexts exist:

1. **Composed (production):** no GEST route — the host page (`scope:web`) renders
   `GestureLiveTestComponent` inline, wiring the current `Comando` draft via
   `@Input`/signal. GEST contributes **no** breadcrumb here (host owns the trail).
2. **Thin host (`apps/gest`, dev/e2e only):** a single lazy route mounts the
   feature standalone so `nx serve/build gest` and `apps/gest-e2e` (Playwright)
   have a web-server base. This route is **not** wired into the shell nav
   (`showInNav: false`, SPEC-05).

## 2. Master route table

| Path | Component | Feature Lib | Guards | Title | Breadcrumb |
|---|---|---|---|---|---|
| `/gest` (thin host only) | `GestureLiveTestComponent` | `libs/gest/feature-live-test` | `authGuard`, `gestDeviceOnlineGuard` | "Teste ao vivo do gesto" | `Calibração › Teste ao vivo` |
| *(composed)* — hosted inside PAC `/pac/pacientes/:id/comandos/:comandoId/calibracao` | `GestureLiveTestComponent` (via host slot) | consumed by `scope:web` page | host guards + `gestDeviceOnlineGuard` (composed) | *(none — host owns)* | *(none — host owns)* |
| *(composed)* — hosted inside onboarding gesture step `/onboarding/gesto` | `GestureLiveTestComponent` (via host slot) | host guards + `gestDeviceOnlineGuard` | *(none — host owns)* | *(none — host owns)* |

Notes:
- The composed rows are **not** GEST-owned routes; they are listed to document
  where the feature appears. The PAC/onboarding paths are authoritative in those
  modules' route maps.
- `apps/gest` host route: `loadComponent: () => import('@neuroassist/gest').then(m => m.GestureLiveTestComponent)` behind `GEST_ROUTES`.

## 3. Guards

| Guard | Source | Responsibility | On failure |
|---|---|---|---|
| `authGuard` | `@neuroassist/shared/access-decision` | authenticated + tenant-scoped session | redirect to login |
| `gestDeviceOnlineGuard` | `libs/gest/access` | requires a **paired, online** `DispositivoEdge` for a *real* live test | allow route but force **degraded mode** (never block; degraded is honest, ADR-005) |

`gestDeviceOnlineGuard` is deliberately **non-blocking**: an offline device does
not deny access — it flips the surface into degraded mode (SPEC-07), because the
caregiver can still preview her camera and get an operator-side confirmation.

## 4. Deep-linking / query params

- The thin host route accepts an optional `?commandDraftRef=<id>` to load a draft
  under test (dev convenience). In production composition the draft arrives via
  `@Input`, never via URL, so no biometric-adjacent context leaks into a URL.

## Critérios de aceite (EARS)

- WHEN the caregiver navigates to a page that composes the live-test feature, THE SYSTEM SHALL render the `GestureLiveTestComponent` inline without a dedicated GEST navigation route.
- THE SYSTEM SHALL omit GEST from the shell navigation menu (`showInNav: false`).
- IF the session is not authenticated when the thin-host `/gest` route is entered, THEN THE SYSTEM SHALL redirect to the login route and SHALL NOT mount the live-test component.
- IF no paired online `DispositivoEdge` is available when the live-test surface activates, THEN THE SYSTEM SHALL activate the surface in degraded mode and SHALL NOT block the route.
- WHILE the live-test feature is composed inside a PAC or onboarding host page, THE SYSTEM SHALL contribute no breadcrumb, leaving the trail owned by the host page.
