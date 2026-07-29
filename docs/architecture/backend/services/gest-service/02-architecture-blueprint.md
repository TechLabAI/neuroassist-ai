<!-- OUTPUT-SCHEMA
- System Boundary
- External Actors and Systems
- End-to-End Flows
- Internal Boundaries
- Cross-cutting Concerns
-->

# 02 — Architecture Blueprint

## System Boundary
`gest-service` is an independent backend microservice within NeuroAssist AI (ADR-002 shared). Its
boundary contains: the TipoGesto primitive catalog, the primitive detectability lifecycle, the
detection-profile builder/publisher, and the published detection contract (schema for
recognized-command reports). The boundary explicitly **excludes** the edge `GestureDetectionEngine`,
which is a separate deployable running on the patient's device (ADR-005 local). gest-service exposes
REST endpoints; the edge is a client of those endpoints, not part of the service process.

## External Actors and Systems
- **Edge agent (`GestureDetectionEngine`)** — pulls the detection profile, reports recognized-command
  telemetry. Separate deployable; treated as an external client at the service boundary.
- **Technical conductor (Danilo)** — via DASH/GWY, manages the catalog and promotes primitives.
- **Peer backend services (separate deployables, synchronous over REST):**
  - **PAC** — reads the TipoGesto catalog + detectability state to validate `Comando` mappings.
  - **EVNT** — consumes the detection contract to construct the business `Evento`.
  - **EDGE** — resolves the current detection profile for a paired device during heartbeat/config sync.
  - **DASH** — surfaces primitive detectability status to the caregiver UI.
- **No message broker** (`messaging.enabled = false`) — all peer interaction is synchronous REST.
- **No TechLab AI / external SaaS** — `ai.hasAi = false`.

## End-to-End Flows
The flows below trace how a primitive becomes detectable and how the edge consumes it. All backend
hops are synchronous REST between independent services (routed via GWY, discovered via REG); the
edge interacts only via pull + report.

### Golden path — catalog to detectable command
1. Conductor registers/selects a primitive → GEST stores it as `registered`.
2. Engine capability is validated (out-of-band in MVP) → conductor promotes it to `supported`
   (via `pending-training` if a training step is expected).
3. PAC builds a `Comando` referencing only `supported` primitives (GEST enforces the guard for
   emergency-urgency commands).
4. EDGE requests the current detection profile for the device → GEST returns the versioned,
   immutable bundle of `supported` primitives + parameters.
5. Edge engine runs classification locally; on a recognized command it posts a report shaped by the
   GEST detection contract → EVNT builds the `Evento`.

### Alternative — primitive not yet detectable
- Caregiver registers "Leitura labial" on Tela 04. GEST keeps it `registered` (not `supported`).
  PAC/DASH show it as "not detectable yet"; it cannot back an emergency command until promoted.
  No false confidence reaches the caregiver.

### Alternative — edge offline
- The edge may be offline; the profile is pull-based, so gest-service is unaffected. When the device
  reconnects (EDGE heartbeat) it pulls the latest profile version and reconciles.

## Internal Boundaries
- **api** — REST controllers: catalog CRUD, lifecycle transitions, profile resolution, detection
  contract publication (OpenAPI is the source of truth for the edge client).
- **application** — use cases: register primitive, transition lifecycle state, build/version profile,
  validate primitive eligibility for a command urgency.
- **domain** — `TipoGesto` aggregate with the detectability state machine; `DetectionProfile`
  aggregate (immutable, versioned); domain invariants (no `supported` regression without audit,
  emergency eligibility rule).
- **infrastructure** — JPA persistence (schema-per-tenant), profile serialization, outbound port to
  EDGE for profile delivery.

## Cross-cutting Concerns
- **Security:** authenticated access via GWY (Spring Cloud Gateway); edge profile pulls use a device
  credential resolved through EDGE pairing. Catalog mutation restricted to the conductor role (ACS).
- **Multi-tenancy (ADR-003 shared):** schema-per-empresa. The TipoGesto catalog is tenant-scoped —
  each unidade de cuidado governs its own vocabulary and detectability state.
- **Observability:** structured logs + pipeline events for every lifecycle transition and profile
  version publication (auditability is a safety requirement, not a nicety).
- **Privacy invariant:** gest-service never receives, stores, or logs any video/image/landmark data —
  only primitive identifiers, states, confidence scalars, and timing metadata.
