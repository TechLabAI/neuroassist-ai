# SPEC-03 — View Models — EVNT (Frontend)

> Module: **EVNT — Eventos e Alarmes** · State owner: `EventDeliveryFacade` (`libs/evnt/data-access`).
> View-models are `readonly` TypeScript `type`s derived from facade Signals (Angular 21, no NgRx).
> Acceptance criteria use EARS notation.

## 1. Core view-model — `EventoVm`

Projection of `EventoDto` (SPEC-04) for the UI. Never carries clinical/biometric content.

```ts
export type Urgencia = 'emergencia' | 'rotina';
export type Feedback = 'nao_avaliado' | 'real' | 'falso_positivo';
export type Tier = 'full_screen_alarm' | 'discreet';

export type EventoVm = {
  readonly eventoId: string;
  readonly urgencia: Urgencia;
  readonly tier: Tier;
  readonly commandLabel: string;        // rótulo livre (pt-BR), from PAC snapshot
  readonly patientName: string;         // resolved for display ("Márcio")
  readonly recognizedAt: string;        // ISO-8601 UTC
  readonly displayTime: string;         // "às 14h32" (derived, pt-BR)
  readonly feedback: Feedback;
  readonly confirmadoPor: string | null;
  readonly confirmadoAt: string | null;
};
```

## 2. Alarm view-model — `AlarmVm` (feature-alarm)

```ts
export type AlarmVm = {
  readonly event: EventoVm | null;      // non-null emergencia ⇒ overlay is shown
  readonly visible: boolean;            // computed: event != null && event.urgencia === 'emergencia'
  readonly confirming: boolean;         // POST /confirmation in flight
  readonly confirmError: AppError | null;
  readonly alreadyConfirmed: boolean;   // 409 path: "Já confirmado por <nome>"
};
```

UI events exposed by `AlarmOverlayComponent`:

| UI event | Payload | Effect |
|---|---|---|
| `confirm` | — | `facade.confirm(eventoId, 'real')` (attendance = real by golden path) |
| (focus trap) | — | keeps focus on the confirm button while `visible` |

## 3. Routine feed view-model — `RoutineFeedVm` (feature-routine)

```ts
export type DeliveryMode = 'live' | 'degraded';

export type RoutineFeedVm = {
  readonly routineQueue: readonly EventoVm[];   // discreet, dismissible notices
  readonly recentEvents: readonly EventoVm[];   // GET /events history page
  readonly deliveryMode: DeliveryMode;
  readonly loading: boolean;
  readonly error: AppError | null;
  readonly empty: boolean;              // computed: !loading && routineQueue.length === 0
};
```

UI events exposed by `EventFeedPageComponent`:

| UI event | Payload | Effect |
|---|---|---|
| `dismiss` | `eventoId` | client-only optimistic removal from `routineQueue` (no server call) |
| `open` | `eventoId` | `router.navigate(['/eventos', eventoId])` |

## 4. Event detail view-model — `EventDetailVm` (feature-routine)

```ts
export type EventDetailVm = {
  readonly event: EventoVm | null;
  readonly loading: boolean;
  readonly error: AppError | null;
  readonly canConfirm: boolean;         // computed: event?.feedback === 'nao_avaliado' && hasPermission('evnt.confirm')
  readonly confirming: boolean;
};
```

UI events:

| UI event | Payload | Effect |
|---|---|---|
| `verdict` | `'real' \| 'falso_positivo'` | `facade.confirm(eventoId, verdict)` |

## 5. Derived / computed selectors (facade)

```ts
activeEvent   : Signal<EventoVm | null>          // .asReadonly()
routineQueue  : Signal<readonly EventoVm[]>      // .asReadonly()
recentEvents  : Signal<readonly EventoVm[]>      // .asReadonly()
deliveryMode  : Signal<DeliveryMode>             // .asReadonly()
selectedEvent : (id: string) => Signal<EventoVm | null>   // computed by id
alarmVm       : Signal<AlarmVm>                  // computed from activeEvent + local confirming
```

## Criterios de aceite (EARS)

- WHILE `activeEvent` is a non-null `emergencia`, THE SYSTEM SHALL expose `AlarmVm.visible = true` so the
  overlay renders.
- WHILE `activeEvent` is `null`, THE SYSTEM SHALL expose `AlarmVm.visible = false` and render no overlay.
- WHILE a confirmation request is in flight, THE SYSTEM SHALL expose `confirming = true` so the confirm
  control is disabled and a spinner shows.
- WHEN the routine queue is empty and not loading, THE SYSTEM SHALL expose `RoutineFeedVm.empty = true`
  so the feed renders its empty state.
- WHILE `deliveryMode` is `degraded`, THE SYSTEM SHALL expose that value so the feed renders the degraded
  banner.
- IF a confirmation returns a terminal-feedback conflict, THEN THE SYSTEM SHALL expose
  `AlarmVm.alreadyConfirmed = true` with `confirmadoPor`/`confirmadoAt` so the overlay shows
  "Já confirmado por <nome>".
- THE SYSTEM SHALL expose `EventDetailVm.canConfirm = false` whenever `feedback` is terminal or the
  caregiver lacks `evnt.confirm`.
