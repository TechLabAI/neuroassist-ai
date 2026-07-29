# SPEC-01 — Route Map — EVNT (Frontend)

> Module: **EVNT — Eventos e Alarmes** · Entry lib: `@neuroassist/evnt` (`libs/evnt/shell-entry`)
> Project: NeuroAssist AI · Shell: `neuroassist-ai-web` (`scope:web`)
> Traces FE ADR-005 (global alarm outlet + live delivery), FE ADR-006 shared (module entry as library).
> Acceptance criteria use EARS notation (guide: `docs/architecture/frontend/standards/ears-notation.md`).

## 1. Routing model

EVNT contributes a **routed sub-tree** (the routine feed + event detail) mounted by the Shell via
`loadChildren`, **plus one non-routed global surface**: the emergency `AlarmOutlet`. The alarm is
deliberately *not* a route (ADR-005) — it is rendered by the Shell above the router-outlet so it
pre-empts whatever route is active. It changes no URL and contributes no breadcrumb.

- **Shell mount:** `loadChildren: () => import('@neuroassist/evnt').then(m => m.EvntModule)`
- **Base segment:** `/eventos` (menu label "Eventos", registered via `EVNT_MODULE_DESCRIPTOR`).
- **Guards:** `authGuard` (shared) + `evntGuard` (evnt-local — caregiver linked to the patient's tenant).

## 2. Master route table

| Path | Component | Feature Lib | Guards | Title | Breadcrumb |
|---|---|---|---|---|---|
| `/eventos` | `EventFeedPageComponent` | `libs/evnt/feature-routine` | `authGuard`, `evntGuard` | "Eventos" | [Eventos] |
| `/eventos/:eventoId` | `EventDetailPageComponent` | `libs/evnt/feature-routine` | `authGuard`, `evntGuard` | "Evento — {rótulo}" | [Eventos › {rótulo} ({hora})] |
| — (non-route) `AlarmOutlet` | `AlarmOverlayComponent` | `libs/evnt/feature-alarm` | (driven by `activeEvent` signal; no guard, no URL) | — (interrupt, no title) | — (no breadcrumb) |

> **Reconciliation note.** The routine feed at `/eventos` renders the `routineQueue` signal and the
> recent-event history (`GET /events`). The event detail hydrates a single Evento (`GET /events/{id}`).
> The emergency path never routes here — it is delivered through the global `AlarmOutlet`.

## 3. Route → data binding

| Route | Data source (SPEC-04) | Facade selector |
|---|---|---|
| `/eventos` | `GET /events/active` (hydrate) + live channel + `GET /events` (history) | `EventDeliveryFacade.routineQueue`, `.recentEvents` |
| `/eventos/:eventoId` | `GET /events/{eventoId}` | `EventDeliveryFacade.selectedEvent(id)` |
| `AlarmOutlet` | live channel (`GET /events/stream`) hydrated by `GET /events/active` | `EventDeliveryFacade.activeEvent` |

## 4. Navigation lifecycle

- On Shell load, `evnt.bootstrap` init hydrates `activeEvent` with one `GET /events/active` **before**
  opening the live channel, so a refresh during an active alarm re-shows the overlay immediately.
- The `AlarmOutlet` never navigates; confirming an alarm mutates the `activeEvent` signal to `null`,
  which unmounts the overlay and leaves the caregiver on her current route.
- `/eventos/:eventoId` deep-links are shareable within a tenant; an unknown/foreign `eventoId` resolves
  to the not-found guard behavior (redirect to `/eventos`).

## Criterios de aceite (EARS)

- WHEN a caregiver navigates to `/eventos`, THE SYSTEM SHALL render the routine feed hydrated from
  `GET /events/active` and the recent-event history.
- WHEN a caregiver navigates to `/eventos/:eventoId` with an id resolvable in her tenant, THE SYSTEM
  SHALL render the event detail for that Evento.
- IF the `authGuard` fails, THEN THE SYSTEM SHALL redirect to the Shell login route and SHALL NOT mount
  the EVNT sub-tree.
- IF the `evntGuard` fails because the caregiver is not linked to the patient's tenant, THEN THE SYSTEM
  SHALL redirect to `/eventos` blocked-state (or Home) and SHALL NOT render event detail.
- IF `/eventos/:eventoId` references an event not found in the caregiver's tenant, THEN THE SYSTEM SHALL
  redirect to `/eventos` and SHALL NOT reveal whether the id exists in another tenant.
- WHILE an `emergencia` `activeEvent` is non-null, THE SYSTEM SHALL render the `AlarmOutlet` above the
  active route without changing the router URL or the breadcrumb.
