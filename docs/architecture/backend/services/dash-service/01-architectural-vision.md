<!-- OUTPUT-SCHEMA
- Intent
- Problem Statement
- Personas
- MVP Scope
- Out of Scope
- Architectural Principles
- Success Signals
-->

# 01 — Architectural Vision

## Intent
DASH is the **caregiver-facing front door** of NeuroAssist AI: the single boundary a human interacts with.
While EVNT, PAC, ACS, HIST and EDGE each own a slice of truth, none of them is shaped for a screen and none
of them, alone, can answer "what does *this* caregiver need to see and do right now". `dash-service` is the
**Backend-for-Frontend (BFF)** that composes those five slices into the panel's views, holds the live
channel that carries an emergency alarm to the caregiver's browser the instant it is raised, and forwards
the caregiver's actions to whichever service actually owns them. Its defining choice is restraint: DASH owns
almost no domain state. It is a lens and a relay, not a system of record — and that is precisely what lets
the backend evolve behind a stable panel and the panel stay simple in front of a distributed backend.

## Problem Statement
The patient's help request is only useful if it reaches a human, fast, and if the human can act on it
without fighting the tooling. But the information a caregiver needs is scattered: EVNT raised the event and
decided the notification tier, PAC holds the command that was recognized and its calibration, HIST holds the
history, EDGE knows whether the device is even alive, and ACS decides which caregiver may see which patient.
Pushing that composition into the browser is the wrong seam: the UI would speak five protocols and five auth
models, poll five endpoints, and break every time a backend service changed shape. Worse, real-time
delivery of an emergency alarm cannot be a best-effort browser poll. DASH solves this by (a) composing
caregiver- and tenant-scoped view models from HIST/PAC/EVNT/EDGE behind one authenticated boundary, (b)
holding a **server-push channel** so EVNT's dispatched notifications reach the open panel immediately and are
acknowledged back, and (c) **proxying** the caregiver's writes — calibration to PAC, confirmations and
acknowledgements to EVNT — so the UI has one place to call while ownership of truth stays where it belongs.

## Problem Statement — non-goals
DASH does not own operational state: EVNT owns the `Evento` and the `NotificationDispatch`, PAC owns the
`Comando` and its calibration, ACS owns the `Cuidador`, HIST owns the audit ledger, EDGE owns the device.
DASH mirrors none of these into its own tables — it reads them live and composes. It does not generate
notifications or decide urgency tier (EVNT's deterministic policy), does not author calibration or confirm
events on its own authority (it proxies those writes), and it never touches video, image or facial-landmark
data. It is the composition-delivery-and-proxy layer for a human — deliberately thin, deliberately not a
source of truth.

## Personas
- **Primary — Jamilly Amaral (family caregiver):** the panel is her whole relationship with the system. She
  needs the `emergencia` alarm to take over her screen the instant Márcio asks for help, routine notices to
  be present but calm, the history to read at a glance, and one obvious control to loosen a command that
  keeps misfiring. She must never be exposed to the fact that five services sit behind the glass.
- **Secondary — Danilo Amaral (technical conductor / clinic coordinator):** uses the same panel, possibly
  across several patients, to read the audit trail, adjust calibration and verify device health during
  onboarding and audits. He needs the panel to honor who-may-see-which-patient and to be honest about a
  stale "offline" indicator.
- **Indirect — Márcio Amaral (patient):** never operates the panel, yet every screen exists so his help
  requests reach a human quickly and his care is legible. The full-screen emergency alarm is, in effect, his
  voice made visible.

## MVP Scope
1. **Panel read-composition (BFF)** — for the authenticated caregiver and tenant, compose each screen's view
   model by fanning out to HIST (timeline), PAC (patient + command catalog + current calibration), EVNT
   (event/alarm state, feedback) and EDGE (device liveness), returning panel-shaped view models rather than
   raw upstream payloads.
2. **Real-time notification delivery** — receive EVNT's `NotificationDispatch` calls, push them to the
   caregiver's connected panel over a **server-push (SSE) channel**, and report `delivered`/`acknowledged`
   back to EVNT, closing the dispatch lifecycle EVNT owns (ADR-005).
3. **On-connect catch-up** — when a panel (re)connects, pull the caregiver's currently unacknowledged
   notifications from EVNT so a disconnect never silently drops an alarm; DASH keeps no durable notification
   store of its own (ADR-005).
4. **Write-through actions** — forward calibration edits to PAC (PAC ADR-004), event confirmations to EVNT
   (EVNT ADR-006) and acknowledgements to EVNT; DASH validates, authorizes and routes but never authors
   truth (ADR-006).
5. **Caregiver scoping** — authenticate the caregiver (OAuth2/JWT aligned with ACS) and enforce tenant +
   caregiver↔patient visibility on every composed read and proxied write, resolved via ACS.

## Out of Scope
- Owning operational/business state for any domain — DASH composes and relays; EVNT/PAC/ACS/HIST/EDGE remain
  the systems of record.
- Generating notifications or deciding urgency tier (EVNT), and authoring calibration or confirming events on
  DASH's own authority (proxied to PAC/EVNT).
- Any async broker/event bus (messaging disabled) and out-of-band escalation channels (SMS/push/phone) —
  panel-only delivery in the MVP.
- Gesture detection, computer vision, device pairing/heartbeat internals and any video/image/landmark data —
  DASH renders only derived state (e.g. an online/offline dot).
- Analytics, reporting dashboards, cross-tenant/global consoles, and durable long-term data retention.

## Architectural Principles
1. **DASH owns no truth, only its presentation and delivery** — it is a Backend-for-Frontend. Every business
   fact is read live from, or written through to, its owner service. The only state DASH may hold is
   ephemeral (live SSE sessions) or presentational (optional UI preferences) (ADR-004).
2. **Delivery of an emergency is a first-class guarantee, not a poll** — the alarm reaches the open panel via
   server-push, and its `delivered`/`acknowledged` signal flows back to EVNT so a missed emergency is
   *visible*, never silent (ADR-005).
3. **One front door, one auth model** — the caregiver authenticates once against DASH (aligned with ACS);
   DASH fans out with the caregiver's scope and never leaks upstream service shapes or auth to the browser.
4. **Composition is caregiver- and tenant-scoped by construction** — every view model and every proxied write
   is bounded by tenant + caregiver↔patient linkage (ACS); no cross-tenant or unauthorized-patient view is
   representable (ADR-003 shared).
5. **Deterministic, AI-free human path** — no inference sits between the patient's request and the
   caregiver's screen (`ai.hasAi = false`); tier, ordering and authorization are pure policy.
6. **Privacy invariant inherited** — DASH handles only structured, derived state; no pixel, frame or
   landmark ever reaches it (project-wide LGPD hard rule).

## Success Signals
- A caregiver opens one panel, authenticates once, and every screen (history, catalog/calibration,
  device status, live alarms) is populated correctly and scoped to exactly the patients they may see.
- An `emergencia` event dispatched by EVNT takes over the caregiver's open panel within seconds, and its
  acknowledgement is reflected back in EVNT's dispatch state — a delivered alarm is provable, a missed one
  is surfaced.
- A caregiver who was briefly disconnected reconnects and immediately sees any notification raised while they
  were away (on-connect catch-up), with nothing duplicated and nothing lost.
- A calibration edit made in the panel takes effect in PAC (and flows on to GEST via PAC→GEST), and a
  confirmation `real`/`falso_positivo` lands in EVNT — with DASH holding no divergent copy of either.
- Turning DASH off loses no business data: because it owns none, the platform's truth is intact and the panel
  is simply unavailable.
