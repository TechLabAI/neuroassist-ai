# 05 — State and Data Flow — DASH (Frontend)

## 1. State topology

| State | Scope | Owner | Exposure |
|---|---|---|---|
| `summary` (HomeSummaryVm) | module-local | `DashHomeFacade` | `signal` → `.asReadonly()` |
| `state` (loading/ok/error) | module-local | `DashHomeFacade` | readonly signal |
| `notifications` (NotificationItemVm[]) | module-local | `NotificationsFacade` | readonly signal |
| `calm` (bool) | derived | `computed()` over injected `activeEvent` | readonly computed |
| `activeEvent` | **module-global (EVNT)** | `EventDeliveryFacade` (`@neuroassist/evnt`) | consumed **at shell**, injected into DASH |
| `device` badge | **module-global (EDGE)** | EDGE facade | composed **at shell** into topbar/Home |
| session / role / `X-Empresa-Id` | app | `@neuroassist/shared/auth-session` + `empresa-context` | injected |

> Only **DASH-owned** state lives in DASH facades. `activeEvent` and `device` are read from
> other modules **at `scope:web`** and passed in — never imported by `scope:dash` (ADR-005).

## 2. Data access pattern

- `DashApiService` (in `data-access`) uses `HttpClient` via `access-http-base`:
  - `GET /dashboard/summary` → HomeSummaryVm source (patient, lastCheck, sync, calm hint).
  - `GET /notifications?scope=recent` → NotificationDispatch list.
  - `PATCH /notifications/{id}/read` → lightweight read-ack (the only write).
- Facades map DTO → VM and hold signals; **no `BehaviorSubject`**, no service exposing
  Subjects. Components (OnPush) read signals.

## 3. Cache strategy

- Default: call `GET /dashboard/summary` on each Home entry (navegação = fresh). No manual
  cache in the MVP.
- The **live calm truth** is not cached — it derives from EVNT's `activeEvent` signal
  (real-time via EVNT's SSE/poll), so the home reflects an emergency without a DASH refetch.

## 4. Optimistic updates

- **None on domain.** DASH writes nothing to the domain. `notification.read` may update the
  local signal immediately (harmless UI ack) with a `PATCH`; on failure it silently reverts
  the `read` flag (no user-facing error — non-critical).
- Home `calm` is **never optimistic** — it follows EVNT's confirmed state (safety).

## 5. Error handling

- `ProblemDetail` (BE) → `AppError` via `@neuroassist/shared/error-model`.
- **Summary fetch error** → StatusCard shows a clear non-technical message + retry
  (`@neuroassist/shared/ui-feedback`); the shell chrome stays intact.
- **Notification list error** → the center shows an inline empty/error state; it never
  blocks Home.
- **Device badge / activeEvent unavailable** (EVNT/EDGE degraded) → the shell composition
  degrades gracefully: badge shows "—/desconhecido", Home stays calm only if there is no
  positive emergency signal (fail-safe toward showing, not hiding, an alarm — EVNT owns this).

## 6. Breadcrumbs and navigation state

- DASH/Status is the **root** of the sidebar; Home contributes the base breadcrumb
  ("Status"). The `libs/dash/navigation` domain lib feeds `BreadcrumbItem[]` for DASH's own
  routes (`Status`, `Notificações`).
- The **alarm overlay contributes no breadcrumb** (global interrupt, no URL change) — EVNT.
- Cross-module navigation (quick-link → HIST) is a route change handled by the shell router;
  the target module owns its own breadcrumb from there.
