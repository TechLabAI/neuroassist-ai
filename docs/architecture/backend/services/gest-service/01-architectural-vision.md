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
GEST is the backend **detection authority** of NeuroAssist AI. It does not run computer vision.
Its purpose is to own the vocabulary of facial-movement primitives the edge engine can recognize,
to govern the honest lifecycle of each primitive (registered name vs. movement actually detectable),
and to hand the edge a versioned detection profile plus a stable contract for reporting a recognized
command. Everything visual and latency-critical stays on the edge; GEST is the server-side truth
about *what is detectable*.

## Problem Statement
People with ALS/MND retain only tiny residual voluntary movements. The product turns those into
help requests. The edge engine can classify a small, changing set of facial primitives, but the
calibration UI (Framing §6.1 Emenda 2) lets a caregiver register an arbitrary new primitive name
(e.g. "Leitura labial") without release. That creates a dangerous gap for a safety product: a
*named* gesture the family trusts may not be a *detectable* movement. Someone must be the single
authority that (a) knows which primitives the engine truly supports, (b) exposes that truth to PAC,
DASH and the caregiver, and (c) feeds the edge a correct, versioned detection profile. That
authority is GEST.

## Problem Statement — non-goals
GEST does not decide whether an event is an emergency (EVNT), does not map a gesture to a family
need (PAC), and does not capture or process any pixel of video (edge). It is the contract and
catalog layer between the edge engine and the rest of the backend.

## Personas
- **Primary — Danilo Amaral (technical conductor):** configures the detection profile, promotes a
  primitive from `pending-training` to `supported` once the engine is validated, and pairs the
  profile to the patient's device during onboarding (Wireframe Tela 05).
- **Secondary — Jamilly Amaral (family caregiver):** on the calibration screen (Tela 04) she selects
  or registers a gesture primitive; GEST is what tells the UI whether that primitive is truly
  detectable yet, so she is never given false confidence in a safety-critical command.

## MVP Scope
1. **TipoGesto catalog** — authoritative, extensible vocabulary of facial-movement primitives
   (seed: sobrancelha, piscar duplo, piscar rápido, piscar devagar, fechar os olhos, mover nariz).
2. **Detectability lifecycle** — each primitive carries a state `registered → pending-training →
   supported`; only `supported` primitives are eligible to back an emergency command.
3. **Detection profile provisioning** — a versioned, immutable bundle (supported primitives +
   classification parameters) that the edge engine pulls via REST/HTTPS.
4. **Detection contract** — the published schema for a recognized-command report from the edge
   (classified primitive id(s), confidence, timing window), the stable input EVNT builds `Evento` on.

## Out of Scope
- Computer-vision execution (webcam, landmarks, classification) — edge deployable, not Java.
- Model training/retraining for new primitives (GEST models the *state*, not the ML pipeline).
- Event/alarm/notification (EVNT), device pairing & heartbeat (EDGE), `Comando` mapping (PAC),
  immutable audit trail (HIST).
- Any transport of video or images off the edge device.

## Architectural Principles
1. **Honesty over convenience** — a primitive name is never presented as detectable until the engine
   confirms it (`supported`). Safety beats a smoother onboarding.
2. **The edge owns pixels; GEST owns vocabulary** — no image ever reaches gest-service.
3. **Detection profile is versioned and immutable** — the edge always knows exactly which profile
   version it is running; profiles are never mutated in place.
4. **Deterministic, not generative** — GEST governs a deterministic CV vocabulary; no LLM, no
   TechLab AI dependency (`ai.hasAi = false`).
5. **Decoupled from edge availability** — gest-service functions whether or not any edge device is
   currently online (the profile is pull-based).

## Success Signals
- Zero emergency commands in PAC can reference a primitive that is not `supported` in GEST.
- The caregiver UI can always answer "is this gesture actually detectable?" from a single source.
- An edge device can bootstrap its detection profile from a cold start with one pull, and knows its
  profile version deterministically.
- Adding a new primitive to the catalog requires no gest-service redeploy; promoting it to
  `supported` is an explicit, audited action.
