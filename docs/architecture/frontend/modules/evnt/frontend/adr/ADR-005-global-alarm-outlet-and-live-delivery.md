# ADR-005 — Emergency alarm as a global shell outlet, delivered over a live channel with polling fallback

- **Status:** proposed (2026-07-28)
- **Scope:** module-local (EVNT)
- **Relates to:** ADR-006 shared (module entry as library), `nx-boundaries.md`,
  `module-pattern.md`

## Context

EVNT's mother-flow (wireframe §4.2) requires that an `emergencia` event be
**impossible to miss**: the moment it is delivered, the caregiver must see a
full-bleed alarm regardless of which screen she is on, and the only way out is an
explicit "Confirmar que atendi Márcio". Two design forces collide with the
default Angular assumptions:

1. **Pre-emption vs routing.** If the alarm were a route/component, a caregiver on
   `/calibracao` or `/historico` would never see it — a route only renders inside
   its own outlet. The alarm must sit *above* the router-outlet.
2. **Delivery with messaging disabled.** The project has
   `stack.messaging.enabled = false` (no broker in the MVP); edge→backend sync is
   REST/HTTPS with a local retry outbox. The FE therefore cannot subscribe to an
   event bus and must learn about new events another way.

## Decision

**(a) The emergency alarm is a global, shell-level outlet, not a route.**
`EvntShellIntegrationService` (`libs/evnt/integration`) registers an
**AlarmOutlet** that the Shell (`neuroassist-ai-web`, `scope:web`) renders above
the router-outlet. It reads the `activeEvent` readonly signal exported by
`@neuroassist/evnt`. When `activeEvent` is a non-null `emergencia`, the outlet
renders `AlarmOverlayComponent` (`role="alertdialog"`, focus trapped to the
confirm button, full-bleed `--color-alarm`). The overlay changes no URL and
contributes no breadcrumb.

**(b) Live delivery via SSE with a polling fallback.** `EvntClient` opens
`GET /events/stream` (Server-Sent Events) to push new events into the facade
signals. If the stream is unavailable or drops, the client transitions
`deliveryMode → 'degraded'` and polls `GET /events/active` on `pollIntervalMs`
(feature-flagged), showing a "modo degradado" banner. On (re)load it hydrates
`activeEvent` with a single `GET /events/active` before opening the stream, so a
refresh during an active alarm re-shows it. No unconfirmed emergency is ever
dropped silently.

## Alternatives considered

1. **Alarm as a top-level route** (`/alarme`) with a guard that force-navigates on a
   new event — rejected: fights the router, loses the caregiver's current context,
   and cannot reliably pre-empt modals; navigation races with confirmation.
2. **WebSocket / STOMP broker for push** — rejected for MVP: messaging is disabled
   by decision; introduces infra the product explicitly deferred until edge/patient
   count grows. SSE needs no broker and rides the existing HTTP/gateway path.
3. **Pure polling only (no SSE)** — rejected as the primary path: poll interval
   trades latency for load; SSE gives sub-second time-to-alarm. Polling is kept
   strictly as the degraded fallback.

## Consequences

- **Positive:** the alarm reliably pre-empts any screen; delivery works without a
  broker; graceful degradation keeps the safety guarantee; `activeEvent` is a single
  signal reused by Home/DASH.
- **Negative:** a shell-level outlet is a deliberate deviation from "everything is a
  route" and couples the Shell to the EVNT `activeEvent` signal (mediated cleanly via
  `@neuroassist/evnt` per ADR-006). SSE lifecycle (reconnect/teardown) adds
  complexity in `data-access`; must be torn down in `evnt.bootstrap` destroy.
- **Testing:** e2e must open each screen, fire an emergency, and assert the overlay
  appears and blocks; and must assert no alarm is lost across a stream drop.

## References

- `_brief.md` §2, §4 · `frontend/02-architecture-blueprint.md` §4 ·
  `frontend/04-frontend-architecture-direction.md` §5 ·
  `frontend/05-state-and-data-flow.md` §2, §5
- Prototype build-plan: `frontend/design-reference/angular-prototype/.build-plans/alarme.plan.md`
- `docs/architecture/frontend/adr/ADR-006-module-entry-as-library.md`
- `docs/architecture/frontend/standards/module-pattern.md`, `nx-boundaries.md`
