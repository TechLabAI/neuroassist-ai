<!-- OUTPUT-SCHEMA
- Ubiquitous Language
- Aggregates and Entities
- Domain Events
- State Transitions
- Invariants
-->

# 03 — Initial Domain Framing

## Ubiquitous Language
- **Fato auditável / Audit Fact:** an action worth remembering — an event was raised/confirmed, a command
  edited, access granted, a device paired. It is what a producer *submits*.
- **AuditRecord (Registro de Auditoria):** the persisted, write-once ledger entry created from an audit fact.
  The aggregate HIST owns. Once recorded it is terminal — no update, no delete.
- **factId:** producer-minted idempotency key; one `factId` ⇒ at most one `AuditRecord`.
- **factType (Tipo de Fato):** the classified kind of fact (e.g. `evento.confirmado`, `comando.sensibilidade.editada`,
  `acesso.concedido`, `dispositivo.pareado`). Drawn from a controlled vocabulary co-owned with producers.
- **subjectRef (Sujeito):** the entity the fact is about — a patient, command, caregiver or device — carried
  as an opaque reference (`{type,id}`), never resolved into HIST's own tables.
- **actorRef (Ator):** *who* performed the action — a caregiver identity resolved by reference from ACS;
  snapshotted onto the record.
- **payload snapshot:** the structured, self-contained detail of the fact at the moment it occurred; never
  re-read from the producer afterwards.
- **Stream:** the ordered sequence of records sharing a (tenant, subject) — the unit over which the hash
  chain is computed and verified.
- **hash chain:** each record's `recordHash = H(prevHash ‖ canonical(record))`, making tampering detectable.
- **occurredAt vs recordedAt:** when the action happened (producer clock) vs when HIST durably stored it.
- **correction (correção):** a new corrective record that references a prior one (`correctsFactId`); never an
  in-place change.

## Aggregates and Entities
> Draft domain shape; the authoritative model is frozen in Specs (`contracts/data-model.md`).

- **`AuditRecord`** — the aggregate root and the only aggregate. Attributes: `id`, `factId` (idempotency),
  `factType`, `subjectRef {type,id}`, `actorRef {caregiverId, displayNameSnapshot}`, `payload` (structured
  snapshot), `occurredAt`, `recordedAt`, `streamKey` (tenant+subject), `sequenceNo` (position in stream),
  `prevHash`, `recordHash`, optional `correctsRecordId`. Immutable after creation.
- **`AuditFactType`** — a value object / controlled enum-like vocabulary of accepted fact types, versioned
  and co-owned with producers (an unknown type is rejected, keeping the ledger meaningful).
- **`SubjectRef` / `ActorRef`** — reference value objects (type + id + snapshot label). Not entities in HIST;
  HIST never owns their lifecycle.
- **`IdempotencyEntry`** — minimal ledger mapping `factId → recordId` guaranteeing exactly-once ingestion.
- **Not entities here:** `Evento`, `Comando`, `Cuidador`, `DispositivoEdge` — owned by EVNT/PAC/ACS/EDGE and
  only referenced.

## Domain Events
Messaging is disabled (MVP), so these are **internal domain events / audit semantics**, not broker messages:
- **`FatoAuditado` (AuditFactRecorded)** — the anchor: a new `AuditRecord` was appended and chained.
- **`FatoDuplicadoIgnorado` (DuplicateFactIgnored)** — a repeated `factId` returned the original record.
- **`CorrecaoRegistrada` (CorrectionRecorded)** — a corrective record linked to a prior one was appended.
- **`CadeiaVerificada` (ChainVerified)** — a stream's integrity was checked (intact / broken@N).

(If a broker is introduced later — ADR-005 — `FatoAuditado` becomes the natural published event and HIST
becomes a subscriber to producers' domain events instead of a synchronous callee.)

## State Transitions
The `AuditRecord` lifecycle is deliberately trivial — that triviality *is* the design:

```
(nonexistent) --record--> recorded [TERMINAL]
```

- There is **no** `edited`, `deleted` or `archived` state. A correction produces a *new* record in state
  `recorded` that references the prior one; the prior record does not change state.
- A duplicate `factId` causes **no** transition — the original record is returned unchanged.

## Invariants
1. **Append-only:** an `AuditRecord`, once `recorded`, is never updated or deleted through any code path
   (ADR-004). Corrections are additive only.
2. **Exactly-once per fact:** for a given tenant, one `factId` maps to exactly one `AuditRecord`
   (idempotent ingestion).
3. **Full attribution:** every record has a non-null `factType`, `subjectRef`, `actorRef`, `occurredAt` and
   `recordedAt`; an incomplete fact is rejected, never partially stored.
4. **Chain integrity:** within a (tenant, subject) stream, `sequenceNo` is strictly monotonic and
   `record.prevHash == previous.recordHash`; the first record's `prevHash` is the genesis constant.
   Verification over a stream must return *intact* unless tampering/gaps exist (ADR-006).
5. **Tenant isolation:** a record's `streamKey`, storage schema and every query are bound to one `empresa`;
   no record or chain spans tenants (ADR-003 shared).
6. **No biometric payload:** `payload` and refs carry only structured business data — never video, image or
   facial-landmark data (project-wide LGPD hard rule).
7. **Monotonic recordedAt:** `recordedAt` is HIST's durable clock and never precedes the prior record's
   `recordedAt` in the same stream (ordering is HIST-authoritative even if producer `occurredAt` skews).
