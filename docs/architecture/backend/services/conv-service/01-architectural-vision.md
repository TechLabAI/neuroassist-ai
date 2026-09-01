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
`CONV` (conv-service) gives a patient with an advanced neurodegenerative disease a **navigable speech
tree** — category → phrase — so they can communicate everyday needs beyond the emergency alarms that
GEST/EVNT already cover. It is a **new module retrofit** into a system already in production
(GEST/EVNT/PAC/ACS/HIST/DASH/EDGE exist as real services): CONV is its own deployable, not an extension
of `pac-service` (ADR-013). Its runtime is **retrieval-only** — it finds and serves curated content, it
never generates it.

## Problem Statement
The production system today covers *structured help requests* (a recognized gesture → a typed `Comando` →
an `Evento`/alarm routed by urgency). It does **not** cover general day-to-day communication: the patient
cannot yet say "I want water with ice" or "reposition me" through the platform. Building that as free-form
generation is unacceptable in an assistive-health context — a hallucinated or mistranslated phrase can
make the patient "say" something they never meant. CONV solves this with a **curated speech tree** the
patient navigates, plus **semantic search** (Elasticsearch/kNN) that jumps to the closest *already
authored* phrase, played back in the patient's **own cached synthesized voice**. Authoring the tree, its
embeddings and its audio is a separate concern (TREI); CONV only serves what TREI has approved.

## Personas
- **Primary — "Paciente com sessão" (patient-with-session).** The person who operates the tree to
  communicate. A **first-class actor** (ADR-012, Opção C), not a caregiver in disguise nor a generic
  device token. Authenticated by a persistent, non-expiring, **revocable** session that the caregiver
  provisions; the actor is **modeled and owned in `acs-service`, not in CONV** — CONV only consumes its
  JWT and enforces the narrow scopes `conv.arvore.navegar` / `conv.no.selecionar`, with zero caregiver
  authority.
- **Secondary — Cuidadora Familiar (Jamilly Amaral).** Does not operate CONV at runtime. She provisions
  and revokes the patient session (in ACS) and authors/curates the tree content in **TREI** (out of CONV
  scope). Relative to CONV she is an administrative provisioner, not a runtime user.
- **Implicit — the patient's device.** The Android/Chromium browser engine that carries *two independent
  identities at once* (ADR-014): an `edge.device` token for reporting gestures via GEST, and the
  patient-with-session token for CONV. CONV cares only about the second.

## MVP Scope
- Serve and navigate the speech tree (root → category → phrase) as `NoArvoreFala`, scoped to one patient
  and one tenant (empresa), schema-per-tenant.
- **Assisted semantic search** over the tree via Elasticsearch kNN, **retrieval-only** — given a captured
  intent, return the closest existing node in the session's active locale. Reuse the existing
  `SearchPort`/`IndexPort` from `elastic-stack-client-lib`.
- **Node selection**: return the phrase text + a **reference** to the pre-cached TTS audio for the active
  locale (the frontend plays it; CONV never synthesizes).
- **Bilingual EN/PT** content served per active session locale; a node may be "active in PT, pending in
  EN" and each locale is served independently (ADR-018).
- **Graceful degradation**: static navigation from PostgreSQL when Elasticsearch is unavailable (OPS-003);
  the frontend falls back to browser Web Speech API when cached audio is absent (OPS-004).
- Consume the patient-with-session JWT from ACS (verify scope) and resolve the shared `engineFeatureRef`
  vocabulary (GEST/PAC) for the gesture→node binding.
- Emit audit-worthy facts (node selected, navigation) to HIST synchronously.

## Out of Scope
- **Authoring/editing/approval of tree content, embedding generation, and ElevenLabs voice synthesis** —
  all belong to **TREI** (ADR-016). CONV runtime **never** calls an LLM (TECH-007, ADR-015) and **never**
  calls the ElevenLabs API (TECH-008, ADR-017).
- **Ownership of the patient-with-session actor** — schema, provisioning, emission and revocation live in
  **ACS** (ADR-012). CONV consumes the token; it does not mint, store, or revoke it.
- Gesture detection / computer vision and the browser detection engine (GEST + edge), device
  pairing/heartbeat (EDGE), event & urgency-tiered alarm generation (EVNT), immutable audit-trail storage
  (HIST), admin panel / calibration rendering (DASH), patient & command catalog authoring (PAC).
- Angular i18n mechanism selection for administrative UI (ADR-018 §5) — a frontend concern.
- Any async broker/event bus (`messaging.enabled = false`) and any object storage for audio (MVP uses a
  simple local filesystem — ADR-017); no biometric/image/landmark data ever touches CONV.

## Architectural Principles
1. **Retrieval, never generation (safety-first).** In assistive health, predictability beats flexibility.
   CONV only returns nodes already curated in TREI; it never composes new text at runtime (ADR-015).
2. **Consume identity, don't own it.** The patient-with-session actor lives in ACS (ADR-012). CONV is a
   pure resource server for it.
3. **New module, isolated blast radius.** CONV owns its own schema for the tree (ADR-013); it does not
   touch the production `pac-service` schema. Integration with PAC/GEST is by shared `engineFeatureRef`
   vocabulary only.
4. **Reuse the platform's existing seams.** Elasticsearch access goes through the already-materialized
   `SearchPort`/`IndexPort` (`elastic-stack-client-lib`); revocation reuses the existing JWT blacklist;
   routing/discovery reuse GWY/REG. No parallel infrastructure.
5. **Degrade, never stop.** Communication must survive a dependency outage: static tree without ES
   (OPS-003), native browser voice without cached audio (OPS-004).
6. **Hexagonal + independent microservice**, consistent with the monorepo (shared ADR hexagonal +
   independent-microservices).

## Success Signals
- A patient can reach any authored phrase in the active locale by navigation **or** by one assisted
   search, and hear it in their cached voice — end to end, on Android/Chromium.
- Semantic search returns the closest authored node with low, predictable latency (local kNN, no external
  round-trip) and **never** returns text absent from the curated tree.
- With Elasticsearch stopped, navigation still works (static fallback); with audio missing, playback still
  works (Web Speech fallback) — no hard failure of the communication path.
- CONV holds **zero** authoring, synthesis, or identity-issuance code — those remain in TREI/ACS,
  confirming the boundary held.
- A caregiver-revoked session is rejected by CONV immediately (blacklist), and every node selection is
  auditable in HIST as coming from the patient session (not the device, not the caregiver).
