<!-- OUTPUT-SCHEMA
- Riscos
- Gaps Conhecidos
- Suposicoes a Validar
- Dependencias Criticas
-->

# 06 — Risks, Gaps, Assumptions

## Riscos
- **R1 — Lost audit facts on synchronous ingestion.** With messaging disabled, an audit fact is only as
  durable as the producer's willingness to retry when HIST is down. If a producer drops the fact on failure,
  the ledger silently misses it — the worst failure mode for an audit system. Mitigation: mandate a producer
  outbox/retry with idempotent replay on `factId` (ADR-005); treat "audit-call failed and was not replayed"
  as a defect, not a tolerated case.
- **R2 — LGPD vs immutability collision.** The ledger is append-only yet holds personal references (patient,
  caregiver, actor label). A per-subject erasure request cannot be satisfied by deleting rows without
  breaking the hash chain. MVP resolves it only at tenant-drop granularity; sub-tenant erasure is unsolved
  (see Gaps). Legal exposure if a data subject demands deletion while the tenant persists.
- **R3 — Chain contention / hot streams.** Appending requires the previous record's hash for the same
  (tenant, subject) stream; a burst of facts for one patient serializes on that stream tip. For pilot volume
  (1 patient) this is negligible, but a naive lock could bottleneck at scale. Mitigation: per-stream (not
  global) serialization + `stream_head` tip cache; revisit if throughput demands.
- **R4 — Fact-type vocabulary drift.** If producers invent fact types ad hoc, the ledger becomes an
  unqueryable junk drawer. Mitigation: a controlled, versioned `factType` vocabulary co-owned with producers;
  reject unknown types. Risk that governance of this vocabulary is neglected across teams.
- **R5 — Weak tamper-evidence guarantee.** Hash-chaining detects tampering only when someone verifies the
  chain; an attacker with DB write access could recompute all downstream hashes. The MVP scheme is
  tamper-**evident** to honest readers, not tamper-**proof**. Stronger guarantees (external anchoring, signed
  checkpoints) are out of scope and must not be over-promised.
- **R6 — Actor attribution depends on ACS.** If ACS is unavailable, actor resolution degrades to the
  producer-supplied snapshot; a compromised or buggy producer could forge `actorRef`. HIST trusts the
  authenticated caller; misattribution risk if producer identity ≠ actor identity is not enforced.

## Gaps Conhecidos
- **G1 — Sub-tenant (per-patient) LGPD erasure** with an immutable ledger is undecided. Options to weigh in
  Specs/legal: crypto-shredding (store personal refs encrypted per-subject, drop the key on erasure, keeping
  the chain intact) vs. a documented legal-basis exception for audit records vs. tenant-drop-only. Not
  resolvable at vision level.
- **G2 — Controlled `factType` vocabulary** is not yet enumerated. The full catalog (EVNT/PAC/ACS/EDGE facts)
  and its versioning/ownership must be pinned in Specs, jointly with producers.
- **G3 — Read authorization model** (which caregiver may read which subject's history) depends on ACS
  linkage semantics not yet frozen; the coordinator-vs-family-caregiver visibility rules need Specs.
- **G4 — Chain verification API shape** (per-stream vs bulk, on-demand vs periodic checkpoint job) is
  sketched but not specified. Whether HIST runs a background integrity sweep in the pilot is open.
- **G5 — Correction semantics** (`correctsFactId`) — whether a subject stream can be corrected across fact
  types, and how DASH renders a corrected timeline, is not yet defined.
- **G6 — Idempotency ledger vs unique index** — whether `audit_idempotency`/`stream_head` are real tables or
  fold into indexes is deferred to Specs (performance decision).

## Suposicoes a Validar
- **A1** — Producers (EVNT, PAC, ACS, EDGE) can and will mint a stable `factId` per audit-worthy action and
  own a retry/outbox for it (mirrors the existing edge→backend sync pattern). *Must be confirmed with each
  producer's Specs.*
- **A2** — Pilot audit volume is low (one patient, one family) → synchronous ingestion and on-demand chain
  verification are sufficient; no broker, no read-model split, no archival tier in the MVP.
- **A3** — The `payload` snapshot is always non-biometric structured data; producers never attempt to send
  images/frames/landmarks. *Enforced by contract + validation; assumed honored.*
- **A4** — Schema-per-empresa with MIG-owned migrations is the tenancy/DDL model for HIST exactly as for the
  other services (ADR-003 shared) — no HIST-specific tenancy deviation.
- **A5** — `recordedAt` (HIST clock) is acceptable as the authoritative ordering even when producer
  `occurredAt` clocks skew; consumers (DASH) sort by `recordedAt` within a stream.

## Dependencias Criticas
- **ACS (`acs-service`)** — actor identity resolution and read-authorization linkage. Degradable (snapshot
  fallback) but required for full attribution and authorization.
- **EVNT / PAC / ACS / EDGE** — the producers whose facts populate the ledger; without their producer-side
  audit calls, HIST is empty. The audit-fact contract is co-owned and must land in each producer's Specs.
- **DASH (`dash-service`)** — the consumer that makes the ledger visible; HIST's read APIs must match DASH's
  "Histórico" needs.
- **MIG (`neuroassist-ai-migrations`)** — materializes HIST tables (incl. the no-UPDATE/DELETE grant) in each
  tenant schema; HIST cannot self-provision DDL.
- **GWY / REG** — routing and discovery for every producer→HIST and DASH→HIST call.
- **PostgreSQL (schema-per-empresa)** — the durable store; its per-schema isolation and (where available)
  privilege model underpin both tenancy and immutability.
