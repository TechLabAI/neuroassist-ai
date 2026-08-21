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
HIST is the **audit and history authority** of NeuroAssist AI. While EVNT, PAC, ACS and EDGE each keep the
*current* state of their domain and may change it freely, HIST is the one component whose only job is to
remember **what happened, who did it, and when** — as a write-once, tamper-evident, tenant-scoped ledger.
It turns the fleeting, mutable operations of the platform into a permanent, provable timeline that a
caregiver can revisit and an auditor can trust. EVNT knows the event's *current* verdict; HIST knows the
*whole story* of how that verdict was reached and can prove it was not altered afterwards.

## Problem Statement
For a patient whose only channel to the world is a small facial movement, care decisions carry weight: was
an emergency alarm real or a false positive, who confirmed it, when was a command's sensitivity changed,
who was granted access to the patient's panel. The operational services answer "what is true now"; none of
them can safely answer "what was true then, and prove no one rewrote it" — precisely because they are
allowed to mutate their own state. Without a dedicated, immutable ledger the platform has no defensible
history: trust rests on memory, accountability is unverifiable, and LGPD traceability of access to patient
data cannot be produced. HIST solves this by (a) ingesting audit-worthy facts from every producing service
idempotently, (b) storing them append-only so no record is ever silently changed or deleted, (c)
hash-chaining records so tampering or gaps are detectable, and (d) serving the ordered, filterable timeline
that DASH renders and auditors consult.

## Problem Statement — non-goals
HIST does not own operational state (EVNT owns the `Evento`, PAC the `Comando`, ACS the `Cuidador`); it
mirrors their audit facts, it does not replace their live tables. It does not detect gestures, run computer
vision, pair devices or dispatch alarms, and it never touches video, image or facial-landmark data. It does
not render the history UI (that is DASH) and it performs no analytics, aggregation or ML over the trail in
the MVP. HIST is the persistence-and-retrieval layer for audit truth — nothing more, deliberately.

## Personas
- **Primary — Jamilly Amaral (family caregiver):** looks back over her patient's timeline to understand the
  honest sequence of alarms and confirmations. She *reads* history and must never be able to edit it — her
  trust depends on the record being exactly what happened.
- **Secondary — Danilo Amaral (technical conductor / clinic coordinator):** during onboarding, tuning and
  audits needs a defensible trail: who changed a sensitivity, who accessed a patient, which emergencies
  were acknowledged. He relies on the ledger being complete and provably unaltered.
- **Indirect — Márcio Amaral (patient) & the data-protection officer:** the patient is the human subject of
  every recorded fact; the DPO treats the ledger as the LGPD record of processing and access to patient
  data. Neither operates HIST directly, but the immutability and attribution guarantees exist for them.

## MVP Scope
1. **Idempotent audit-fact ingestion** — accept an `AuditFact` from any producing service (EVNT, PAC, ACS,
   EDGE) over REST via GWY, using a producer-supplied `factId` as the idempotency key so a retried delivery
   never produces a duplicate ledger record.
2. **Immutable append-only storage** — persist each fact write-once into the tenant ledger; the API exposes
   no update or delete, and a correction is a new fact that references the prior one (ADR-004).
3. **Tamper-evident chaining** — each record carries a hash chained to the previous record in its
   (tenant, subject) stream, so any alteration or missing record is detectable when the timeline is read
   (ADR-006).
4. **History & audit query** — retrieve the ordered timeline for a patient/subject and filter by fact type,
   actor and time window; this is the read surface DASH renders as "Histórico".

## Out of Scope
- Owning operational/current state for any domain — HIST mirrors audit facts, it does not replace EVNT/PAC/
  ACS/EDGE tables.
- Emitting notifications/alarms (EVNT) and rendering the history/calibration UI (DASH).
- Gesture detection, computer vision, device pairing/heartbeat (GEST/EDGE) and any video/image/landmark data.
- Analytics, aggregation, ML over the trail, and cold archival/warehousing tiers.
- Any async broker/event bus (messaging disabled) and any cross-tenant / global audit console.

## Architectural Principles
1. **Write-once, never rewritten** — the ledger is append-only; there is no API path that mutates or deletes
   a recorded fact. Immutability is the product, not a feature toggle (ADR-004).
2. **Every fact is attributable** — a record always answers who (actor), what (fact type + payload snapshot),
   which subject (patient/command/caregiver ref) and when (occurred-at + recorded-at). An unattributed fact
   is rejected, not stored.
3. **Tampering is detectable, not merely discouraged** — hash-chaining per (tenant, subject) stream makes a
   silent edit or a removed record provably visible on read (ADR-006).
4. **HIST is a sink, never a source of operational truth** — it snapshots references (actor, subject) it does
   not own, and never becomes the place other services read live state from.
5. **Privacy invariant inherited** — audit payloads carry only structured business facts and references; no
   pixel, frame or landmark ever reaches HIST (project-wide LGPD hard rule). The ledger is itself an LGPD
   asset: access to it is audit-worthy too.
6. **Tenant isolation** — every record and every query is strictly scoped to its `empresa`
   (schema-per-tenant, ADR-003 shared); no cross-tenant read is representable.

## Success Signals
- Every audit-worthy action in the platform (event raised/confirmed, command edited, access changed, device
  paired) appears exactly once in the ledger, even under producer retries and reconnects.
- No API call can alter or remove a recorded fact; a correction always appears as a new, linked record and
  the original remains visible.
- Reading a patient's timeline verifies the hash chain and surfaces any break — the pilot can *prove* the
  history is intact, not just assert it.
- A caregiver can reconstruct "what happened to my patient" and a coordinator can answer "who did X and when"
  in one query, scoped to their empresa, with complete actor attribution.
