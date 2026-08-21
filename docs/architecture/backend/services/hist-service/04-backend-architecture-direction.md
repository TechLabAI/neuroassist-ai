<!-- OUTPUT-SCHEMA
- Stack
- Service Boundary
- Package Organization
- Hub Service Dependencies
- External Integrations
- Async vs Sync Decisions
-->

# 04 — Backend Architecture Direction

## Stack
- **Language/Runtime:** Java 21.
- **Framework:** Spring Boot 3.5.x (Spring Web, Spring Data JPA, Bean Validation, Spring Security OAuth2
  resource server).
- **Persistence:** PostgreSQL 15+ (transactional, schema-per-`empresa`); JDBC/JPA with request-time schema
  pinning.
- **Migrations:** Flyway, **centralized in the MIG service** (no boot-time DDL in hist-service) — ADR-003 shared.
- **Discovery/Routing:** Eureka (REG) for registration/discovery; Spring Cloud Gateway (GWY) as the edge.
- **Inter-service calls:** synchronous REST (Feign or `RestClient`) for the one outbound dependency (ACS
  actor resolution). No message broker (`stack.messaging.enabled = false`).
- **Observability:** structured JSON logging + Micrometer metrics + trace propagation via `X-Correlation-Id`.
- **AI:** none (`ai.hasAi = false`).

## Service Boundary
**Single service (ADR-002 shared: independent microservices).** `hist-service` is one deployable Spring Boot
process owning the audit ledger and its read APIs — it is *not* decomposed further (no separate "ingestion"
vs "query" services in the MVP: same aggregate, same store, one bounded context). It is deliberately a
**sink**: it accepts audit facts and serves history, and it does not host any producer's operational logic.
The write side (idempotent append + chaining) and the read side (timeline query + chain verification) live
in the same service because they share the `AuditRecord` aggregate and the tenant schema; a CQRS/read-model
split is explicitly deferred until read volume or query shape justifies it (doc 06).

## Package Organization
Hexagonal layering (ADR-001 shared). Base package `com.neuroassist.hist`:

```
com.neuroassist.hist
├── api            # inbound REST adapters: controllers, DTOs, request validation, tenant-context filter
│   ├── audit      #   POST /audit-facts, GET /audit-facts/{id}
│   └── history    #   GET /history (timeline query), GET /history/verify (chain check)
├── application    # use cases: RecordAuditFact, QueryHistory, VerifyChain; transactional orchestration; port ifaces
│   └── port       #   AuditLedgerPort, IdempotencyPort, ActorDirectoryPort (outbound), inbound use-case ifaces
├── domain         # AuditRecord aggregate, HashChain VO, FactType, SubjectRef/ActorRef VOs, invariants (framework-free)
└── infrastructure # outbound adapters: JPA append-only repo, hash service, idempotency store, ACS client, config, security, observability
```

Rule: `domain` depends on nothing; `application` depends on `domain` + its own port interfaces;
`infrastructure` and `api` depend inward and implement/consume ports. No `jakarta.persistence` or Spring
annotations leak into `domain`.

## Hub Service Dependencies
No TechLab AI / hub bounded-context dependency (`ai.hasAi = false`). HIST needs none of orchestration,
knowledge, document_composition or analytics. Its only cross-service runtime dependency is **ACS**, and only
to *resolve/validate the actor reference* attached to a fact (and, on reads, to authorize a caregiver by
patient linkage). Even that is optional-by-design: the `actorRef` snapshot is carried on the fact by the
producer, so ACS resolution is a best-effort enrichment/validation, not a hard blocker for recording an
audit fact (an emergency confirmation must be recorded even if ACS is momentarily slow).

## External Integrations
- **Inbound producers (REST via GWY):** EVNT, PAC, ACS, EDGE → `POST /audit-facts` with `factId`
  idempotency.
- **Inbound consumer (REST via GWY):** DASH → `GET /history…` and chain verification.
- **Outbound (REST via GWY/REG):** ACS `ActorDirectory` — resolve/validate `actorRef` and caregiver↔patient
  linkage for read authorization. Adapter behind `ActorDirectoryPort`; failures degrade to snapshot-only.
- **Data:** PostgreSQL per-tenant schema (owned operationally; DDL via MIG).
- **No** direct edge, video, model or third-party channel integrations — HIST never touches biometric data.

## Async vs Sync Decisions
- **Ingestion is synchronous REST (MVP).** Producers call HIST in-band; durability against transient HIST
  outages is provided by the **producer's local outbox/retry queue** (the same edge→backend sync pattern in
  project-context), replayed idempotently on `factId`. Rationale and the deferred broker alternative are in
  **ADR-005**.
- **Idempotency is mandatory** on the write path — exactly-once semantics under retry are non-negotiable for
  an audit ledger; a duplicate `factId` returns the original record with no second write.
- **Reads are synchronous** request/response; the chain-verification is computed on demand over the requested
  stream window.
- **Broker deferral:** if audit volume or fan-out grows (multiple consumers, cross-service subscriptions),
  the natural evolution is producers publishing domain events and HIST subscribing — revisited in ADR-005 and
  gated by the same trigger that would introduce messaging project-wide.
