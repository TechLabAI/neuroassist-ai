<!-- OUTPUT-SCHEMA
- System Boundary
- External Actors and Systems
- End-to-End Flows
- Internal Boundaries
- Cross-cutting Concerns
-->

# 02 — Architecture Blueprint

## System Boundary
`hist-service` is an independent Spring Boot microservice (ADR-002 shared) that owns exactly one thing: the
**immutable audit ledger**. Inside the boundary are the audit-fact ingestion endpoint, the append-only
write-once persistence of `AuditRecord`, the per-(tenant, subject) hash chaining, and the history/audit
read APIs. Outside the boundary are: the producers that *generate* audit facts (EVNT, PAC, ACS, EDGE), the
consumer that *renders* them (DASH), the identities HIST references but does not own (ACS caregivers, PAC
commands, EVNT events), and all infrastructure services (GWY routing, REG discovery, MIG migrations). HIST
never calls a producer back and never mutates operational state — it is a terminal sink for facts and a
source only for read-only history.

## External Actors and Systems
- **EVNT (`evnt-service`)** — primary producer. Posts audit facts for event lifecycle transitions (raised,
  notified, confirmed `real`/`falso_positivo`, resolved).
- **PAC (`pac-service`)** — producer. Command calibration/sensitivity edits, command retire/version facts.
- **ACS (`acs-service`)** — producer *and* the referenced authority for actor identity: access grants/
  revocations, caregiver↔patient linkage changes. HIST snapshots the actor ref ACS provides.
- **EDGE (`edge-service`)** — producer. Device pairing / credential lifecycle facts that are audit-worthy.
- **DASH (`dash-service`)** — consumer. Reads the history/audit query APIs to render the "Histórico" screen.
- **GWY (API Gateway)** — all producer→HIST and DASH→HIST traffic is REST routed through the gateway.
- **REG (Eureka)** — service discovery; HIST registers and is discovered by callers.
- **MIG (migrations)** — owns Flyway; materializes HIST's tables in each tenant schema (ADR-003 shared).
- **Human actors** are indirect: the caregiver/coordinator act *through* producing services and DASH, never
  against HIST directly.

## End-to-End Flows
The write path is idempotent append; the read path reconstructs and verifies a timeline. Both are REST via GWY.

### Golden path — record an audit fact (write)
1. A producing service (e.g. EVNT, upon a caregiver confirming an event) performs its own operation and, in
   the same request handling, calls `POST /audit-facts` on HIST via GWY, carrying: `factId` (producer-minted
   idempotency key), tenant context, `factType`, `subjectRef` (patient/command/caregiver), `actorRef` (the
   caregiver from ACS), `occurredAt`, and a structured `payload` snapshot.
2. HIST resolves the tenant, pins the schema, and checks the idempotency ledger on `factId`.
3. If new: HIST computes the record hash chained to the previous record of the same (tenant, subject) stream
   and **appends** the `AuditRecord` write-once; returns `201` with the record id and chain position.
4. If duplicate `factId`: HIST returns the original record (`200`), no second write — the producer's retry is
   safe.

### Alternative — correction (never an edit)
- A producer that must correct a prior fact posts a **new** `AuditFact` of a corrective type carrying
  `correctsFactId`. HIST appends it as a new chained record; the original stays visible. There is no update
  or delete path (ADR-004).

### Read path — reconstruct the timeline
1. DASH (or a coordinator via DASH) calls `GET /history?subjectRef=…&type=…&from=…&to=…` via GWY, tenant-scoped.
2. HIST returns the ordered records and, on request, a **chain-verification result** (intact / broken at
   position N), so the reader can trust the timeline is complete and unaltered (ADR-006).

### Degradation
- If HIST is momentarily unreachable, a producer's audit call fails; because the fact is safety/accountability
  relevant, producers hold it in their **local outbox/retry queue** (same pattern as edge→backend sync, per
  project-context) and replay it — idempotency on `factId` makes replay exact-once. HIST itself never queues
  outbound work.

## Internal Boundaries
Hexagonal layering (ADR-001 shared):
- **api (inbound adapter):** REST controllers — `POST /audit-facts`, `GET /history`, `GET /audit-facts/{id}`,
  chain-verification endpoint. DTO validation, tenant-context extraction, auth. No business logic.
- **application (use cases):** `RecordAuditFact` (idempotent append + chaining), `QueryHistory`,
  `VerifyChain`. Orchestrates domain + ports; owns the transactional unit of work.
- **domain (core):** `AuditRecord` aggregate, the append-only invariant, the hash-chain value object, fact
  types, subject/actor reference value objects. Pure, framework-free, no update/delete semantics expressed.
- **infrastructure (outbound adapters):** JPA/JDBC append-only repository (tenant-schema pinned), hash
  computation, idempotency-ledger store, ACS actor-resolution client (by reference), observability.
- **Ports** invert every dependency: the application depends on `AuditLedgerPort`, `IdempotencyPort`,
  `ActorDirectoryPort` interfaces; infrastructure implements them.

## Cross-cutting Concerns
- **Security:** every request is authenticated (OAuth2 resource server / JWT, aligned with ACS as authority);
  producers call with a service/actor identity, DASH with a caregiver identity scoped to its empresa. HIST
  authorizes reads by tenant + caregiver↔patient linkage. Access to the ledger is itself an audit-worthy fact.
- **Observability:** structured logs, metrics and traces correlated by `X-Correlation-Id`; key signals are
  ingestion rate, duplicate-`factId` rate, chain-verification failures (must be zero), and read latency.
- **Multi-tenancy (ADR-003 shared):** schema-per-`empresa`; tenant resolved at request time and pinned for
  the unit of work. Records and chains are per-tenant; no cross-tenant read is representable.
- **Immutability & integrity:** enforced in the domain (no mutating operations) and defended in persistence
  (append-only repository, DB privileges without UPDATE/DELETE on ledger tables where feasible) plus the hash
  chain for tamper-evidence (ADR-006).
- **Privacy/LGPD:** payloads carry only structured business facts and references — no biometric/video data
  (project-wide hard rule). Subject-erasure requests are handled at the tenant/patient boundary; because the
  ledger is immutable, erasure is realized by tenant-schema drop at off-boarding rather than per-row deletion
  (open tension flagged in doc 06).
