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
EVNT is the **event and alarm authority** of NeuroAssist AI. When the edge recognizes the patient's
active command, EVNT is what turns that recognition into a persisted, structured business `Evento`,
decides how urgently to alert, routes the alert to the responsible caregiver, and records whether the
alert was real or a false positive. GEST knows *what is detectable*; EVNT knows *what happened and who
must respond*. It is the safety-critical hinge between detection and human help.

## Problem Statement
A recognized gesture that no one sees is a failed help request. For a patient with ALS/MND whose only
channel to the world is a small facial movement, the cost of a missed emergency is severe and the cost
of a false alarm is caregiver fatigue and lost trust. The system therefore needs a single component
that (a) reliably and idempotently ingests the edge's recognized-command report, (b) turns it into a
durable event that survives restarts and reconnections, (c) applies a deterministic urgency policy so
an emergency screams and a routine request whispers, (d) routes to the right caregiver, and (e) lets
that caregiver tell the system whether the alarm was genuine — feeding a false-positive signal back
into tuning. That component is EVNT.

## Problem Statement — non-goals
EVNT does not run computer vision or own the primitive vocabulary (edge + GEST), does not define what a
gesture *means* or how urgent it is (that policy lives on the `Comando`, owned by PAC), does not pair
devices (EDGE), and does not store the immutable audit ledger (HIST) or render the panel (DASH). EVNT
is the orchestration and system-of-record layer for the `Evento` itself.

## Personas
- **Primary — Jamilly Amaral (family caregiver):** on the alarm screen she is alerted to a help
  request, acts, and marks it `real` or `falso_positivo`. EVNT must guarantee she is never *not*
  alerted for an emergency, and that dismissing a false positive is one honest action.
- **Secondary — Danilo Amaral (technical conductor):** configures who is responsible for a patient's
  events and monitors the false-positive rate to tune sensitivity during onboarding.
- **Indirect — Márcio Amaral (patient):** the human behind every Evento; the reason emergency latency
  and delivery reliability are treated as non-negotiable invariants, not features.

## MVP Scope
1. **Idempotent report ingress** — accept the edge `RecognizedCommandReport` (validated against GEST),
   using `reportId` as the idempotency key so a retried delivery never produces a duplicate Evento.
2. **Evento as system of record** — persist the structured event (patient, command reference, urgency
   snapshot, timing, `feedback = nao_avaliado`); EVNT owns the event's lifecycle end to end.
3. **Urgency-tiered notification** — resolve `urgencia` from the referenced `Comando` and dispatch the
   matching tier: **full-screen alarm** for `emergencia`, **discreet notice** for `rotina`, routed to
   the responsible caregiver (via ACS).
4. **Confirmation feedback loop** — a caregiver transitions the event `nao_avaliado → real |
   falso_positivo`; the transition is audited and records `confirmadoPor`.

## Out of Scope
- Computer-vision execution and primitive detectability (edge + GEST).
- Authoring `Comando`, its urgency/sensitivity, and patient registration (PAC).
- Device pairing, credential issuance and heartbeat (EDGE).
- Immutable audit-trail storage (HIST) and panel/history rendering (DASH).
- Any async broker/event bus (messaging disabled) and any third-party push channel in the MVP.
- Any video, image or facial-landmark data touching evnt-service.

## Architectural Principles
1. **An emergency is never silently lost** — ingress is idempotent and durable; a report that reaches
   EVNT results in exactly one Evento, and delivery failure of a notification is a recorded, retryable
   fact, not a swallowed exception.
2. **Urgency is deterministic policy, not inference** — the alert tier is a pure function of the
   command's `urgencia`; no AI, no heuristics that could downgrade an emergency.
3. **EVNT owns the Evento; it borrows everything else** — command meaning (PAC), detectability (GEST),
   caregiver identity (ACS) are resolved by reference and *snapshotted* onto the Evento, never re-owned.
4. **Honest false positives** — dismissing a false alarm is a first-class, audited transition that
   improves the system, not an error state hidden from tuning.
5. **Privacy invariant inherited** — EVNT carries only primitive ids, confidence and timing; no pixel,
   frame or landmark ever reaches it (project-wide LGPD hard rule).
6. **Tenant isolation** — every Evento and notification is strictly scoped to its `empresa`
   (schema-per-tenant, ADR-003 shared).

## Success Signals
- Every valid recognized-command report yields exactly one Evento, even under retries and reconnects.
- Zero missed emergencies in pilot: every `emergencia` Evento produces a delivered, acknowledged alarm
  or a recorded delivery failure that is retried.
- A caregiver can classify any event `real` / `falso_positivo` in one action, and the false-positive
  rate is queryable per patient/command for tuning.
- EVNT keeps functioning (persisting events, queuing notifications) even when DASH or a caregiver is
  momentarily offline — the Evento is durable and the alert re-delivers.
