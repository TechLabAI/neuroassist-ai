<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-004 — Evento as System of Record (Idempotent Ingress)

## Status
aceita

## Contexto
The edge produces a `RecognizedCommandReport` (GEST detection contract §3.1). GEST **validates** that
report but explicitly **does not persist** it (GEST event-contracts §5: `RecognizedCommandReport` →
"Persisted: no (GEST)"). Something must be the durable, operational system of record for the resulting
business event, and it must survive edge retries, reconnects and downstream outages. The edge is expected
to retry delivery (`reportId` is a client-generated UUID idempotency key), so naive ingestion would
produce duplicate events for a single help request — unacceptable for a safety product where each event
maps to a real human need. Messaging is disabled (`stack.messaging.enabled = false`), so there is no
broker-provided dedup or durable queue to lean on.

## Decisão
`evnt-service` is the **system of record for `Evento`**. Ingress is **idempotent on `reportId`** via a
tenant-scoped uniqueness guard (idempotency ledger / unique index): a new `reportId` creates exactly one
`Evento`; a duplicate returns the original Evento/verdict with no side effects. The Evento is **validated
(via GEST) and persisted before any notification is attempted** ("durable-first"), so no help request is
lost to a later dispatch failure. EVNT snapshots the borrowed facts it needs (command label, `urgencia`,
timing) onto the Evento rather than re-reading them later.

## Consequências
**Positivas:** exactly-once event creation under retries; the Evento survives restarts and downstream
outages; a clean ownership boundary (GEST = validation, EVNT = record, HIST = audit trail); snapshotting
makes an Evento immutable against later `Comando` edits.
**Negativas / trade-offs:** EVNT must maintain an idempotency guard and reason about the retry window
(≥24h guidance); persisting before notifying adds a write on the critical path (accepted — durability
beats latency for a safety event); snapshot data can drift from the live `Comando`, which is intentional
but must be understood by consumers (DASH/HIST read the snapshot, not the live command).
