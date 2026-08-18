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
- **Framework:** Spring Boot 3.5.x (Spring Web incl. **SSE** via `SseEmitter`/reactive `Flux` streaming,
  Bean Validation, Spring Security OAuth2 resource server). No persistence framework is required by the MVP
  core (DASH owns no domain store); Spring Data JPA is included only if the optional UI-preference table is
  built.
- **Persistence:** none in the MVP core. If the optional `caregiver_ui_preference` table is added,
  PostgreSQL 15+ schema-per-`empresa` with request-time schema pinning, DDL via MIG.
- **Migrations:** Flyway, **centralized in the MIG service** — DASH runs no boot-time DDL (ADR-003 shared);
  in the MVP DASH likely contributes **no migrations at all**.
- **Discovery/Routing:** Eureka (REG) for registration/discovery; Spring Cloud Gateway (GWY) as the edge for
  both browser→DASH and DASH↔upstream traffic.
- **Inter-service calls:** synchronous REST (`RestClient`/Feign) to HIST, PAC, EVNT, EDGE, ACS — all reads
  and write-throughs. No message broker (`stack.messaging.enabled = false`).
- **Browser channel:** **Server-Sent Events (SSE)** held open by DASH for live notification push; the panel
  falls back to long-poll where SSE is unavailable.
- **Observability:** structured JSON logging + Micrometer metrics + trace propagation via `X-Correlation-Id`.
- **AI:** none (`ai.hasAi = false`).

## Service Boundary
**Single service (ADR-002 shared: independent microservices).** `dash-service` is one deployable Spring Boot
process that is the **Backend-for-Frontend** for the caregiver panel — it is not decomposed further (no
separate "composition" vs "notification-gateway" services in the MVP; both share the same caregiver-scope,
auth and SSE session context). It is deliberately a **thin façade/relay**: it composes reads, delivers
notifications and proxies writes, and it hosts **no** other service's operational logic and **no** system of
record. The choice not to build a durable read-model or a persisted notification inbox here is intentional
(ADR-004 / ADR-005) — the sources of truth already persist and retry; DASH duplicating them would create a
second, divergent copy of safety-critical state.

## Package Organization
Hexagonal layering (ADR-001 shared). Base package `com.neuroassist.dash`:

```
com.neuroassist.dash
├── api            # inbound adapters: panel REST controllers (/panel/**), SSE endpoint (/panel/stream),
│   ├── panel      #   GET view models, POST/PUT caregiver actions (write-through)
│   ├── stream     #   SSE session open + heartbeat + on-connect catch-up trigger
│   └── internal   #   POST /internal/notifications (EVNT dispatch sink, service-to-service auth)
├── application    # use cases: ComposePatientOverview, ComposeHistory, DeliverNotification,
│   └── port       #   ReplayUnacknowledged, Proxy{Calibration,Confirmation,Acknowledgement}; outbound + inbound port ifaces
├── domain         # PanelViewModel VOs, CaregiverScope rule, PanelSession, notification-tier semantics (framework-free)
└── infrastructure # outbound REST adapters (HIST/PAC/EVNT/EDGE/ACS), in-memory SSE session registry,
                   #   security (OAuth2), observability, optional UI-preference store
```

Rule: `domain` depends on nothing; `application` depends on `domain` + its own port interfaces;
`infrastructure` and `api` depend inward and implement/consume ports. No HTTP-client, Spring or SSE type
leaks into `domain`.

## Hub Service Dependencies
No TechLab AI / hub bounded-context dependency (`ai.hasAi = false`). DASH needs none of orchestration,
knowledge, document_composition or analytics — the human path must be deterministic and inference-free. Its
runtime dependencies are the **project's own services**: ACS (identity + caregiver↔patient scope, consulted
on every request), PAC (command catalog/calibration read + calibration write-through), EVNT (notification
dispatch sink + unacknowledged pull + delivery/ack callbacks + confirmation write-through), HIST (history
read) and EDGE (device liveness poll). ACS is effectively a hard dependency (no scope resolution ⇒ no
authorized view); the read upstreams are **degradable per section** (a down upstream blanks only its slice of
the view model, ADR-004).

## External Integrations
- **Browser (Angular panel) → DASH (REST + SSE via GWY):** view-model reads, caregiver actions, and the
  live notification stream.
- **EVNT → DASH (REST via GWY):** `POST /internal/notifications` dispatch (service-to-service auth); DASH
  reports `delivered`/`acknowledged` back.
- **DASH → EVNT (REST via GWY/REG):** pull unacknowledged notifications on connect; proxy event confirmations
  (`real`/`falso_positivo`) and acknowledgements. Behind `EventStatePort` / `NotificationDispatchBackPort`.
- **DASH → PAC (REST):** read patient + command catalog + calibration; write-through calibration edits (PAC
  ADR-004). Behind `CommandCatalogPort`.
- **DASH → HIST (REST):** read the history/audit timeline. Behind `HistoryQueryPort`.
- **DASH → EDGE (REST):** poll device liveness (ETag/304, `DASH poll 15s`). Behind `DeviceLivenessPort`.
- **DASH → ACS (REST):** authenticate/authorize and resolve caregiver↔patient scope. Behind
  `IdentityDirectoryPort`.
- **No** direct edge, video, model, database-of-record or third-party integrations — DASH touches no
  biometric data and owns no business store.

## Async vs Sync Decisions
- **All service-to-service traffic is synchronous REST (MVP).** EVNT→DASH dispatch, DASH's upstream reads and
  all write-throughs are in-band request/response over GWY. There is no broker (`stack.messaging.enabled =
  false`).
- **The browser channel is server-push (SSE), not a broker and not a poll.** DASH holds the connection and
  pushes EVNT's notifications the instant they are dispatched; long-poll is the fallback transport. Rationale
  and the broker-deferred alternative are in **ADR-005**.
- **Reads are composed with parallel fan-out** and per-upstream timeouts; a slow/failed upstream degrades its
  own section of the view model rather than blocking the whole response.
- **The notification path fails open:** an unreachable/disconnected caregiver is reported to EVNT (whose
  fail-open retry holds the emergency), never absorbed by DASH. Delivery/ack callbacks are idempotent on
  `dispatchId`.
- **No durable queue or read-model in DASH:** because EVNT already persists and retries dispatch state and the
  upstreams already persist their truth, DASH deliberately holds no inbox and no cached read-model in the MVP;
  a read-model/cache is deferred and gated by measured fan-out latency (doc 06). **Broker deferral:** if
  messaging is later introduced project-wide, EVNT→DASH dispatch and DASH→EVNT signals become the natural
  topics with no change of meaning.
