# SPEC-02 — Component Hierarchy — EVNT (Frontend)

> Module: **EVNT — Eventos e Alarmes** · Traces FE ADR-005 / ADR-006 shared.
> Prose spec (component trees) — **no EARS section** (per D-446).

## Conventions

- **Smart** components consume `EventDeliveryFacade` (signals) and declare the signals they read.
- **Presentational** components receive `input()` and emit `output()` only — no facade, no HTTP.
- All components are `standalone`, `OnPush`, Signals-based (Angular 21).

## 1. `libs/evnt/feature-alarm` (type:feature) — emergency overlay

Rendered by the Shell `AlarmOutlet` (not a route). The only action is confirmation.

```
AlarmOverlayComponent            (smart · role="alertdialog" · full-bleed --color-alarm)
  reads signals:
    - activeEvent   : Signal<EventoVm | null>   (EventDeliveryFacade)
    - confirming    : Signal<boolean>            (local)
    - confirmError  : Signal<AppError | null>    (local)
  ├── UrgencyBadgeComponent      (presentational · input: urgencia)      [libs/evnt/ui]
  ├── EventTimestampComponent    (presentational · input: recognizedAt)  [libs/evnt/ui]
  ├── AlarmMessageComponent      (presentational · input: commandLabel, patientName)
  └── ConfirmAttendanceButton    (presentational · input: disabled; output: confirm)
        emits (confirm) → AlarmOverlayComponent.onConfirm()
```

## 2. `libs/evnt/feature-routine` (type:feature) — routine feed + detail

```
EventFeedPageComponent           (smart · route /eventos)
  reads signals:
    - routineQueue  : Signal<EventoVm[]>         (EventDeliveryFacade)
    - recentEvents  : Signal<EventoVm[]>         (EventDeliveryFacade)
    - deliveryMode  : Signal<'live'|'degraded'>  (EventDeliveryFacade)
  ├── DegradedModeBannerComponent (presentational · input: deliveryMode) [@neuroassist/shared/ui-feedback]
  ├── RoutineNoticeComponent      (presentational · input: evento; output: dismiss, open)
  │     emits (dismiss) → EventFeedPageComponent.onDismiss(id)
  │     emits (open)    → router → /eventos/:id
  └── EventListComponent          (presentational · input: events; output: open)

EventDetailPageComponent         (smart · route /eventos/:eventoId)
  reads signals:
    - selectedEvent : Signal<EventoVm | null>    (EventDeliveryFacade.selectedEvent(id))
    - confirming    : Signal<boolean>
  ├── UrgencyBadgeComponent       (presentational)                        [libs/evnt/ui]
  ├── EventTimestampComponent     (presentational)                        [libs/evnt/ui]
  ├── FeedbackVerdictControl      (presentational · input: feedback, disabled; output: verdict)
  │     emits (verdict: 'real'|'falso_positivo') → EventDetailPageComponent.onVerdict()
  └── ConfirmationTrailComponent  (presentational · input: confirmadoPor, confirmadoAt)
```

## 3. `libs/evnt/ui` (type:ui) — shared presentational bits

```
UrgencyBadgeComponent    (input: urgencia: 'emergencia'|'rotina' → token-driven color)
EventTimestampComponent  (input: recognizedAt: string → "às 14h32" pt-BR)
```

## 4. Non-visual providers (not components)

```
libs/evnt/integration : EvntShellIntegrationService  → registers AlarmOutlet (SPEC-06)
libs/evnt/access      : evntGuard                     → CanActivateFn (SPEC-01)
libs/evnt/navigation  : EvntNavConfig                 → breadcrumb/menu (SPEC-06)
libs/evnt/data-access : EvntClient + EventDeliveryFacade (SPEC-03, SPEC-04)
```

## 5. Composition rule (critical)

The `AlarmOverlayComponent` lives in a **shell-level outlet**, never inside a route. Only `scope:web`
(the Shell) may depend on every module; it reads the `activeEvent` signal re-exported by
`@neuroassist/evnt` and renders `AlarmOverlayComponent` above the router-outlet (ADR-005). Home and
DASH consume the same `activeEvent` signal at page-level composition — they never import EVNT feature
libs directly.
