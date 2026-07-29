# 01 — Architectural Vision — EVNT (Frontend)

## 1. Intent

At the end, when Márcio makes his emergency gesture, Jamilly — wherever she is on
the panel — sees the **whole screen become an unmistakable alarm** ("Márcio
precisa de ajuda", full-bleed red), and the only thing she can do is press
**"Confirmar que atendi Márcio"**. Routine commands (e.g. "água") arrive as a
calm, discreet notification that never hijacks her attention. EVNT-FE is the
**alarm and notification surface** of the product: it does not decide urgency
(that comes from the bound `Comando`), it makes a business event *impossible to
miss when it matters and easy to ignore when it doesn't*.

## 2. Problem statement

**Current journey (without EVNT-FE):** a recognized gesture has nowhere to
surface. Even if the backend stores an event, the caregiver has no live,
attention-appropriate channel — an emergency could sit unseen behind whatever
screen is open, and routine events would either be missed or would interrupt as
loudly as emergencies. For a safety-critical channel both failure modes are
unacceptable.

**New journey:** every generated event is delivered live and *rendered by
urgency*. `emergencia` pre-empts the active screen with a full-bleed alarm that
persists until an explicit human confirmation (wireframe §4.2, the product's
mother-flow); `rotina` shows a discreet, dismissible notification (§4.6). The
active alarm state also drives the Home "Márcio está bem" surface — no active
emergency ⇒ green/calm, active ⇒ alarm.

## 3. Personas (job-to-be-done)

- **Jamilly Amaral (Cuidadora Familiar) — primary.** JTBD: "The moment Márcio
  needs help, make it impossible for me to miss, and let me tell the system I've
  got him." Lives in the alarm overlay + confirmation, and glances at routine
  notifications.
- **Coordenador de clínica — secondary.** JTBD: "Route the emergency to whoever
  is on shift for this patient and let us confirm attendance." Same alarm surface,
  tenant = clínica, multiple linked caregivers.

## 4. MVP scope (in)

- **Emergency alarm overlay** — full-bleed, `--color-alarm`, pre-empts any active
  screen; single action = confirm attendance; timestamp of the event shown.
- **Routine notification** — discreet in-app banner/toast for `rotina` events,
  non-blocking and dismissible.
- **Confirmation flow** — pressing confirm writes `confirmadoEm` + `confirmadoPor`
  and clears the active-alarm state (only human confirmation clears it).
- **Live delivery** — the panel learns about a new event in near-real-time
  (transport per ADR-005), degrading to polling when needed.
- **Active-state signal** — an `activeEvent` signal that Home/DASH consume to
  answer "Márcio está bem?" without extra navigation.
- **State machine** surfaced: `idle → routine-notice | alarm-active →
  (confirming) → confirmed → idle`, plus `delivery-degraded`.

## 5. Out of scope (MVP)

- History browsing, filters and `Evento.feedback` (real / falso-positivo) — that
  is **HIST-FE** (wireframe §4.4 / §4.7).
- Command catalog / urgência editing / calibração — **PAC-FE**.
- Device online-offline badge and pairing — **EDGE-FE** (EVNT only *reads* the
  active event; the badge is composed at the shell page).
- The recognition itself and its signal schema — **GEST** / edge.
- Off-panel channels (SMS, e-mail, mobile push) — post-MVP.
- Any AI / suggestion behaviour — deterministic urgency only (`ai.hasAi = false`).

## 6. Architectural principles

- **Standalone-first** — every EVNT component is `standalone: true`; the single
  `NgModule` is only the `evnt.module.ts` lazy entry (ADR-006 / module-pattern).
- **Signals-first** — `activeEvent`, alarm state and routine queue live in
  `signal<T>` inside the facade, exposed via `.asReadonly()`; no NgRx, no manual
  `BehaviorSubject`.
- **Lazy-by-default** — the module is loaded via `@neuroassist/evnt`.
- **Interruptive-at-the-shell** — the emergency overlay is a **global, shell-level
  outlet**, not a per-route component, so it can pre-empt whatever screen is
  active (see doc 02 §4 and ADR-005).
- **OnPush mandatory** on every component; the alarm must repaint deterministically.
- **Urgency is data, not code** — the full-bleed treatment is triggered by the
  bound `Comando.urgencia = emergencia`, never inferred from the label.
- **Confirmation is the only exit** — no timeout, no auto-dismiss for an emergency;
  the alarm clears only on explicit `confirmadoPor`.
- **Boundary discipline** — `scope:evnt` imports only `scope:evnt` + `scope:shared`;
  cross-module composition (Home, calibração) happens only at `scope:web`
  (nx-boundaries.md).
- **Tenancy** — every backend call carries `X-Empresa-Id`; routing respects the
  patient↔caregiver vínculo (ACS).

## 7. Success signals

- **Time-to-alarm** — from event delivery to full-bleed overlay visible ≤ 1 s;
  the overlay pre-empts any active route.
- **Confirmation integrity** — 100% of confirmations persist `confirmadoEm` +
  `confirmadoPor`; the alarm never clears without a human confirmation
  (asserted in e2e).
- **Urgency parity** — every `emergencia` renders full-bleed and every `rotina`
  renders discreet; zero mis-classification of the visual treatment.
- **Delivery resilience** — when the live channel drops, the panel falls back to
  polling and shows a "modo degradado" banner; no silent loss of an active alarm.
- **Attention hygiene** — routine notifications never block interaction and are
  dismissible; measured 0 full-screen interrupts for `rotina` events.
