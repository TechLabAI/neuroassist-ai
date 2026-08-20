# HIST — Event Contracts (`hist-service`)

> Module: **HIST — Histórico e Auditoria** · Service: `hist-service` · Package: `com.neuroassist.hist`
> **Messaging: DISABLED** (`stack.messaging.enabled = false`) — **REST-based, broker-deferred** (ADR-005).
> Traces ADR-005 (synchronous audit ingestion) and the domain-event semantics of vision doc 03.

## 0. Status: no broker in the MVP

NeuroAssist AI runs with **no message broker** in the MVP. There is therefore **no topic, no queue, no
published event and no consumed event** in the classic messaging sense. This document exists to make that
explicit and to define the **REST-based equivalent** of HIST's ingestion "events": the audit fact
submitted by a producer over HTTP.

The **audit-fact POST is the event.** Each producer (EVNT, PAC, ACS, EDGE) emits an audit fact by calling
`POST /api/v1/hist/audit-facts` synchronously (ADR-005), carrying its own idempotency key (`factId`). This
is the transport that a broker would otherwise provide, with the same at-least-once + idempotent-consumer
guarantees, realized over REST + a producer-side retry outbox.

**Broker-deferred:** if audit volume or fan-out grows (multiple audit consumers, cross-service
subscriptions), the natural evolution is producers publishing domain events and HIST subscribing. That
evolution is gated by the same trigger that would introduce messaging project-wide (ADR-005). The names in
§3 are the events those topics would carry.

---

## 1. Ingestion "events" (REST) — consumed by HIST

The full request/response contract lives in `api-contract.md §2`. Summary of the audit-fact intake as an
event contract:

| Attribute | Value |
|---|---|
| Transport | HTTP `POST /api/v1/hist/audit-facts` (via GWY, discovered via REG) |
| Producers | EVNT, PAC, ACS, EDGE (service credentials, scope `hist:write`) |
| Idempotency key | `factId` (producer-minted uuid, unique per tenant) |
| Delivery semantics | at-least-once (producer outbox/retry) + idempotent consumer (HIST) ⇒ effectively exactly-once |
| Ordering | none required across facts; per-stream order is HIST-assigned at append (`sequenceNo`, `recordedAt`) |
| Tenant | JWT `tenant` claim (schema-pinned); never cross-tenant |
| Payload versioning | envelope is `/api/v1`; `payload` is producer-defined, self-contained, no biometric keys |

**EARS — ingestion as event contract**
- WHEN a producer submits an audit fact with a new `factId`, THE SYSTEM SHALL treat it as a
  once-and-only-once intake, append the record, and acknowledge `201 Created`.
- WHEN a producer redelivers the same `factId` (retry/outbox replay), THE SYSTEM SHALL acknowledge
  `200 OK` with the original record and SHALL NOT create a duplicate (idempotent consumer, ADR-005).
- THE SYSTEM SHALL NOT require producers to preserve inter-fact ordering; per-stream chain order is
  assigned by HIST at append time.
- IF a producer submits an unknown `factType` or a forbidden (biometric) payload key, THEN THE SYSTEM
  SHALL reject with `422` and the producer's outbox SHALL treat it as a poison message (no infinite retry).

### 1.1 Producer contract (durability obligation)

Because there is no broker, durability against a transient HIST outage is the **producer's** obligation
(ADR-005): each producer keeps a local outbox/retry queue and replays on `factId`. HIST cannot enforce
this; it is contracted in each producer's own Specs. HIST's side of the contract is: idempotent intake,
stable `factId` semantics, and never losing an acknowledged fact.

**EARS**
- WHERE a producer holds an unacknowledged audit fact, THE PRODUCER SHALL retain it in its outbox and
  replay it until HIST acknowledges `201`/`200` (producer-side; asserted here for the end-to-end contract).

---

## 2. Read "events" — none

HIST publishes nothing and pushes nothing. Consumers (DASH, coordinators via the FE) **pull** history and
verification over REST (`api-contract.md §3`). There is no change-feed, webhook, or subscription in the
MVP. When a broker is introduced, `FatoAuditado` (§3) becomes the natural published event that DASH would
subscribe to instead of polling.

---

## 3. Internal domain-event semantics (not transported)

These are **domain facts inside HIST**, used for logging/metrics and as the future published-event names
(vision doc 03). They are **not** emitted anywhere in the MVP.

| Domain event | Meaning | Future topic (deferred) |
|---|---|---|
| `FatoAuditado` (AuditFactRecorded) | a new `AuditRecord` was appended and chained | `hist.audit-fact.recorded` |
| `FatoDuplicadoIgnorado` (DuplicateFactIgnored) | a repeated `factId` returned the original record | (log/metric only) |
| `CorrecaoRegistrada` (CorrectionRecorded) | a corrective record linked to a prior one was appended | `hist.correction.recorded` |
| `CadeiaVerificada` (ChainVerified) | a stream's integrity was checked (intact / broken@N) | (metric only) |

**EARS — internal semantics**
- WHEN an append succeeds, THE SYSTEM SHALL emit an internal `FatoAuditado` signal (structured log +
  `hist_audit_fact_recorded_total` metric) — not a broker message.
- WHEN a duplicate `factId` is short-circuited, THE SYSTEM SHALL emit `FatoDuplicadoIgnorado`
  (`hist_duplicate_fact_ignored_total`) and SHALL NOT append.

---

## 4. Idempotency (key + window)

| Aspect | Value |
|---|---|
| Key | `factId` (uuid), unique per tenant (`uq_audit_record_fact_id`, `audit_idempotency.pk`) |
| Scope | tenant schema (`t_<empresa>`) |
| Window | ≥ 24h (aligned with edge/EVNT retry windows); pilot retains indefinitely |
| Effect on duplicate | return original record, `200 OK`, no second write, no chain advance |

**EARS**
- THE SYSTEM SHALL persist `factId → recordId` on first intake and SHALL consult it before any chaining so
  a duplicate never advances the stream `sequenceNo`.
