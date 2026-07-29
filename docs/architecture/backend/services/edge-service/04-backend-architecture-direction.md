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
Canonical project stack, no deviations: **Java 21**, **Spring Boot 3.5.x**, **PostgreSQL 15+**
(schema-per-empresa), **Eureka** (REG) for discovery, **Spring Cloud Gateway** (GWY) for routing,
centralized **Flyway** via the MIG service. **No message broker** (`stack.messaging.enabled = false`).
**No TechLab AI** dependency (`ai.hasAi = false`). Security via the shared OAuth2 resource-server
boundary (`jwk-set-uri`), with an additional device-authentication mechanism for the edge agent (ADR-005).

## Service Boundary
`edge-service` is a **single, independent microservice** (ADR-002 shared — no further backend↔backend
decomposition). Its boundary is the device-integration bounded context: registration, pairing,
heartbeat/liveness, and profile-pointer delivery. It deliberately does **not** absorb detection
authority (GEST) or patient data (PAC). The only "split" in this domain is cloud↔edge — the CV agent is
a separate deployable outside this Java service — and that split is owned by GEST's ADR-005, not
reopened here. EDGE talks to the edge agent as a REST server; it never runs on the device.

## Package Organization
Hexagonal (ADR-001 shared), base package `com.neuroassist.edge`:
```
com.neuroassist.edge
├── api            # REST controllers (device: register/pair/heartbeat; internal: liveness/validate), DTOs
├── application    # use cases / ports-in: RegisterDevice, PairDeviceToPatient, UnpairDevice,
│                  #   RecordHeartbeat, EvaluateLiveness, ResolveDeviceForPatient, DeliverProfilePointer
├── domain         # DispositivoEdge aggregate, pairing state machine, liveness policy, invariants
└── infrastructure # JPA repos (per-empresa schema), DetectionProfilePort→GEST client,
                   #   device-credential verifier, tenant routing, config
```

## Hub Service Dependencies
- **TechLab AI:** none.
- **REG (Eureka):** registers for discovery; resolves GEST by service name.
- **GWY (Gateway):** all inbound traffic (edge agent + internal consumers) is routed via GWY; EDGE is
  not exposed directly.
- **MIG:** owns all DDL; EDGE contributes migration scripts to MIG's managed set, ships no local Flyway.
- **Inter-module (sync REST, no broker):**
  - **→ GEST:** pull the active detection-profile version pointer for a patient (outbound `DetectionProfilePort`).
  - **← PAC:** inbound device-validation endpoint used by PAC's `EdgeDevicePort` at write time.
  - **← DASH:** inbound liveness-query endpoint (polled — see Async vs Sync).

## External Integrations
- **Edge agent (REST/HTTPS client):** the sole external caller of register/pair-claim/heartbeat; an
  in-product process on the patient's device, not a third-party API. Contract is versioned (OpenAPI),
  authored in `/be-architect-specs`.
- **No third-party vendors** in the MVP (no push-notification service, no MDM). Revisit if remote alerting
  or fleet management is needed later.

## Async vs Sync Decisions
- **All calls synchronous REST.** With `messaging.enabled = false`, EDGE cannot publish a "device went
  offline" event. Liveness is exposed on a **query endpoint that DASH polls**; the derived `offline`
  state is computed at read time from heartbeat freshness (no scheduler needed to flip state). This is
  the accepted MVP trade-off (doc 06): polling latency on DASH bounds how quickly a caregiver sees a
  drop. If a broker is later enabled, `DispositivoFicouOffline` becomes the natural first published event.
- **Profile delivery is pull-based, piggybacked on the heartbeat** — the device learns the current
  pointer in the heartbeat response, decoupling EDGE from GEST/edge availability and giving a natural
  drift-detection point.
