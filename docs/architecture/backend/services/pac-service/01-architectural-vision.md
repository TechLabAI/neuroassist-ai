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
PAC is the **catalog authority** of NeuroAssist AI: the place where a family says *who the patient is*
and *what each of their gestures means and how sensitively it should be detected*. It owns the
`Paciente` record and the per-patient `Comando` catalog — each command a mapping from a gesture (one
primitive, or a composite of two) to a free-text need the family chose, calibrated independently for
sensitivity, gesture timing and urgency. GEST knows *what is detectable*; EVNT knows *what happened*;
PAC knows *what the patient's movements are supposed to mean*. It is the semantic source of truth the
rest of the system reads from.

## Problem Statement
A residual facial movement has no universal meaning — a raised eyebrow means "água" for one family and
"chamar a cuidadora" for another. Someone must let the family author that mapping, in their own words,
and tune how hard the system should try to detect each gesture: too sensitive and every twitch is a
false alarm, too dull and a real emergency is missed. Crucially, each command needs to be calibrated
*independently* — loosening the "água" gesture must not loosen the "emergência" gesture. And an
emergency command must never be mapped to a gesture the detection engine cannot actually recognize. PAC
is the single component that (a) registers the patient and its context (tenant, device, caregivers by
reference), (b) lets the family author and label a command catalog, (c) calibrates sensitivity, timing
and urgency per command, and (d) guarantees an emergency command is backed by a genuinely detectable
gesture before it goes live.

## Problem Statement — non-goals
PAC does not run computer vision, own the primitive vocabulary, or publish detection profiles (GEST). It
does not turn a recognized command into a business event, notify anyone, or capture false-positive
feedback (EVNT). It does not own caregiver identity, authentication or access roles (ACS), nor pair
devices or track heartbeats (EDGE), nor store the immutable audit ledger (HIST) or render the panel
(DASH). PAC authors the catalog; others detect, react to and display it.

## Personas
- **Primary — Jamilly Amaral (family caregiver):** registers the patient and authors each `Comando`
  with a label she understands, choosing the gesture and urgency and adjusting sensitivity/timing when
  detection misbehaves (Wireframe "onboarding", "calibracao"). PAC must let her recalibrate one command
  without touching the others.
- **Secondary — Danilo Amaral (technical conductor):** seeds the initial catalog and reconciles *what
  the family wants a gesture to mean* with *what the engine can detect* — validating (via GEST) that an
  emergency command is backed by a `supported` primitive before it is published.
- **Indirect — Márcio Amaral (patient):** the person the catalog exists for; never a user, but the
  reason a mis-calibrated emergency command is a safety defect rather than a UX annoyance.

## MVP Scope
1. **Patient registration** — create and maintain the `Paciente` (identity, its `Tenant`/empresa, and
   references to its `DispositivoEdge` and responsible caregivers); PAC is the system of record for the
   patient.
2. **Command catalog authoring** — create, edit and retire a `Comando` for a patient: map a gesture
   (principal `TipoGesto` + optional secondary, referenced by id) to a free-text family label and need.
3. **Independent per-command calibration** — each `Comando` carries its own `sensibilidade`
   (`baixa|media|alta`), `tempoMinGestoMs`/`tempoMaxGestoMs` window and `urgencia` (`emergencia|rotina`),
   editable in isolation.
4. **Emergency eligibility gate** — before an `emergencia` command is published, PAC validates via GEST
   that every referenced primitive is `supported`; an emergency can never map to an undetectable gesture.
5. **Catalog publication for detection** — expose the patient's active, calibrated command set so GEST
   can snapshot its calibration params into an immutable `DetectionProfile` version pulled by the edge.

## Out of Scope
- Computer-vision execution, primitive vocabulary/detectability and detection-profile publication (GEST).
- Event generation, urgency-tiered notification and false-positive feedback (EVNT).
- Caregiver/user identity, authentication, roles and tenant provisioning (ACS).
- Device pairing, credential issuance and heartbeat (EDGE).
- Immutable audit-trail storage (HIST) and panel/history/calibration rendering (DASH).
- Any async broker/event bus (messaging disabled) and any biometric/image/landmark data touching PAC.

## Architectural Principles
1. **PAC owns meaning, not detection** — it authors the `Comando` (label, need, urgency, calibration)
   and *references* the primitive; detectability and profiles belong to GEST. PAC never re-implements CV.
2. **Independent calibration is a hard boundary** — editing one command's sensitivity/timing/urgency
   must have zero effect on any sibling command (no shared mutable calibration).
3. **An emergency is never mapped to an undetectable gesture** — the emergency-eligibility gate against
   GEST is a publish-time invariant, not advice; safety over convenience.
4. **The catalog is the family's language** — labels are free-text and family-authored; PAC imposes no
   controlled vocabulary on *what a gesture means*, only on *how it is calibrated and referenced*.
5. **Edits must not corrupt history** — a `Comando` referenced by past events is versioned/retired, not
   silently mutated, so EVNT/HIST snapshots stay meaningful (see ADR-006).
6. **Privacy invariant inherited** — PAC stores only catalog metadata and primitive ids; no pixel, frame
   or landmark ever reaches it (project-wide LGPD hard rule).
7. **Tenant isolation** — every `Paciente` and `Comando` is strictly scoped to its `empresa`
   (schema-per-tenant, ADR-003 shared).

## Success Signals
- A caregiver can register a patient and author a labeled, calibrated command in the onboarding flow
  without technical help.
- Recalibrating one command's sensitivity/timing never changes the behavior of any other command.
- No `emergencia` command can be published unless GEST confirms its primitive(s) are `supported` — the
  gate rejects undetectable emergencies at authoring time, not at detection time.
- GEST can pull a coherent, calibrated command set per patient to build a detection profile, and EVNT/DASH
  resolve a command's label and urgency consistently.
- Editing or retiring a command leaves historical events/audit references intact and interpretable.
