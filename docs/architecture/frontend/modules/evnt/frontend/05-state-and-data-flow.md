# 05 — State and Data Flow — EVNT (Frontend)

## 1. State topology

| State | Scope | Owner | Exposure |
|---|---|---|---|
| `activeEvent` (the unconfirmed emergency) | module-global | `EventDeliveryFacade` (`libs/evnt/data-access`) | `.asReadonly()` signal, re-exported by `@neuroassist/evnt` |
| `routineQueue` | module-local | `EventDeliveryFacade` | `.asReadonly()` signal |
| `deliveryMode` (`live`/`degraded`) | module-local | `EventDeliveryFacade` | `.asReadonly()` signal |
| `confirming` / `confirmError` | feature-local | `feature-alarm` component | local signal |
| session / tenant (`X-Empresa-Id`) | app-global | `@neuroassist/shared/access` | shared interceptor |

There is **one** cross-module read: `activeEvent` is consumed by the Shell
AlarmOutlet and by Home/DASH via page-level composition. Everything else is
module-local.

## 2. Data access pattern

- `EvntClient` (in `data-access`) uses `HttpClient` through
  `@neuroassist/shared/access-http-base`. Two channels:
  1. **Live delivery** — `GET /events/stream` (SSE) mapped into an RxJS stream, or
     the polling fallback `GET /events/active`.
  2. **Command actions** — `POST /events/{id}/confirm`.
- The client pushes into facade signals; the facade never exposes RxJS subjects to
  components. Components read signals only (OnPush).
- No `BehaviorSubject` fan-out by hand; the SSE/interval Observable is the only
  reactive source and it terminates on `bootstrap` teardown.

## 3. Cache strategy

- **Default: no cache.** The active event is authoritative from the live channel /
  poll; the routine queue is in-memory for the session only.
- On (re)load, hydrate `activeEvent` with one `GET /events/active` before opening
  the live channel (so a refresh during an active alarm re-shows it immediately).
- No local persistence of clinical content; the queue is discarded on logout.

## 4. Optimistic updates

- **Confirm is not optimistic.** The alarm clears **only after** the confirm POST
  succeeds — a safety invariant (never show "attended" before it is recorded).
  While `confirming`, the button is disabled and a spinner shows; on failure the
  alarm returns to `alarm-active` with an inline retry.
- Routine dismissal *is* client-only optimistic (no server round-trip; purely a
  view concern).

## 5. Error handling

- Backend `ProblemDetail` → `AppError` via `@neuroassist/shared/error-model`.
- **Confirm failure** → inline message in the overlay ("Não foi possível registrar.
  Tentar de novo."); alarm stays active. Never a toast that could be missed.
- **Late/duplicate confirm** (409) → treat as success-equivalent: show "Já
  confirmado por <nome> às <hora>" and clear the overlay.
- **Live channel error** → transition to `delivery-degraded`, start polling, show a
  banner via `@neuroassist/shared/ui-feedback`; recover to `live` when the stream
  reconnects.
- **Routine delivery error** → silent retry on next poll; routine is best-effort and
  must not raise alarm-level noise.

## 6. Breadcrumbs and navigation state

- `libs/evnt/navigation` feeds `BreadcrumbItem[]` for the routine feed / event
  detail routes (e.g. `Eventos › Notificações` / `Eventos › <rótulo> (14h32)`).
- The **alarm overlay contributes no breadcrumb** — it is a global interrupt, not a
  navigational location; it does not change the router URL.
- `evnt.nav.config.ts` registers the routine-feed menu entry via
  `EVNT_MODULE_DESCRIPTOR`.
