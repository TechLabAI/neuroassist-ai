# 01 — Architectural Vision — EDGE (Frontend)

## 1. Intent

At the end, during setup, the technical conductor can **pair the room's edge
device to Márcio in under a minute** — request a code, watch the agent claim it,
and see it come *online · version X* — and from then on **anyone looking at the
panel can trust, at a glance, whether monitoring is actually live**. EDGE-FE is
the platform's **connectivity conscience**: it pairs the local agent and then
tells the honest truth about whether it is watching — `online`, `offline`, or —
crucially — `stale/unknown` when the device has simply gone quiet. It never
detects, never alarms; it answers one question truthfully: *is the watcher awake?*

## 2. Problem statement

**Current journey (without EDGE-FE):** pairing is a black box and connectivity is
a single boolean someone hopes is current. There is no honest signal for the most
dangerous state of all — a device that has silently stopped reporting but still
shows its last `status = online`. A caregiver could believe Márcio is being
watched while the agent has been dead for hours. Silence is invisible.

**New journey:** pairing is a **guided, observable** flow (request code → claim →
first heartbeat → confirmed), and connectivity is a **derived, freshness-based
truth**, not a trusted flag. When heartbeats stop arriving past a defined
threshold, the surface does not keep asserting `online`; it degrades to
`stale/unknown` and, once confirmed, `offline` — loudly (ADR-005 local). The
device becomes a first-class, self-describing object with a truthful state that
the whole platform reads.

## 3. Personas (job-to-be-done)

- **Danilo Amaral (technical conductor) — primary, onboarding + operations.**
  JTBD: "Pair the device to the patient once, confirm it's genuinely online, and
  give me a place to re-pair or replace it when it fails." Uses onboarding step 3
  and the device-detail view.
- **Jamilly Amaral (Cuidadora Familiar) — primary, monitoring.** JTBD: "Never let
  me assume monitoring is live when it isn't — show connectivity always, and make
  a lost connection impossible to miss." Never pairs; consumes the always-visible
  connectivity chip the Shell renders from EDGE's read-model.
- **Márcio Amaral (patient) — indirect.** JTBD (implicit): "If the watcher stops,
  someone must know immediately." A falsely-green device is a safety defect.

## 4. MVP scope (in)

- **Device pairing flow** (onboarding step 3): request a pairing code from
  `edge-service`, present code + instructions, **poll** until the agent claims it
  and first heartbeat arrives, then confirm *"paired · online · version X"*
  (mirrors prototype `onboarding.component.ts` step 3, made honest).
- **Honest connectivity state** — derive `online | offline | stale/unknown` from
  heartbeat freshness (`ultimaSincronizacao`) against an explicit staleness
  threshold; never render a persisted `status` flag blindly (ADR-005 local).
- **Device read-model for the Shell** — expose the active patient's
  `DispositivoEdge` as a signal read-model consumed by the **Shell topbar** and
  **Home** connectivity chip (Wireframe §3.2.B — connectivity is never hidden).
- **Device detail / management view** — last-sync time, agent version, current
  derived state, and a **re-pair / unpair** action to replace a failed device.
- **One active device per patient** (MVP) — pairing a new device supersedes the
  previous bind explicitly.

## 5. Out of scope (MVP)

- Gesture detection / webcam classification / detection-profile execution — runs
  **inside** the agent, modeled by GEST; EDGE only observes the agent through
  `edge-service`.
- Patient record and command-catalog authoring (PAC) — EDGE is referenced by the
  patient's device id; it does not author catalog data.
- Caregiver identity, auth, roles, tenant provisioning (ACS).
- Event/alarm generation and false-positive feedback (EVNT) — EDGE's offline
  signal is an *input* to EVNT, not an alarm EDGE raises.
- Audit-trail storage (HIST) and broader panel/history rendering (DASH).
- Any broker/websocket/SSE (messaging disabled) — connectivity is REST + polling.
- Agent OTA/firmware updates, multi-device fleets, remote agent config push.

## 6. Architectural principles

- **Standalone-first** — every EDGE component is `standalone: true`; the single
  `NgModule` is only the `edge.module.ts` lazy entry (ADR-006 / module-pattern).
- **Signals-first** — pairing and connectivity state live in `signal<T>` inside
  EDGE facades, exposed via `.asReadonly()`; no NgRx, no manual `BehaviorSubject`.
- **Lazy-by-default** — the module loads via `@neuroassist/edge`; the pairing and
  device-detail views are `loadComponent`-lazy.
- **OnPush mandatory** on every component.
- **Honest-connectivity discipline** — the surface never asserts `online` without
  fresh heartbeat evidence; unknown ≠ online, and staleness is a first-class state
  (ADR-005 local), mirroring PAC's honest-sync and GEST's honest-degraded posture.
- **Boundary discipline** — `scope:edge` imports only `scope:edge` + `scope:shared`;
  the Shell composes EDGE's device read-model at `scope:web`, never importing EDGE
  internals (nx-boundaries.md, ADR-006).
- **Observe, don't drive** — EDGE watches the agent through `edge-service`; it
  never commands the agent or performs detection.

## 7. Success signals

- **Pairing time** ≤ 60 s p95 from "request code" to confirmed first heartbeat.
- **Silence latency** — a device that stops reporting is reflected as
  `stale/unknown` within one staleness window (≤ configured threshold) and never
  keeps showing `online` past it (asserted in e2e).
- **Connectivity honesty** — 100% of "online" states are backed by a heartbeat
  fresher than the threshold; no green chip without evidence (ADR-005).
- **Always-visible** — the connectivity chip is present on every authenticated
  screen (topbar) and reflects the derived state, not a stale flag.
- **p95 status poll** ≤ 500 ms and **p95 pairing-code request** ≤ 1 s against the
  API gateway.
