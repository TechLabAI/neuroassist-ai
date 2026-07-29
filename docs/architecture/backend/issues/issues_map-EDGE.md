# Issues Map -- EDGE

> Module: **EDGE — Integração de Dispositivo Edge** · Service: `edge-service` · Port: 8087
> Generated: 2026-07-29 · Total: 21 functional + 7 integration = 28 issues

## By Issue

| Issue | Title | Kind | Priority | Status |
|---|---|---|---|---|
| EDGE-001 | Create edge-service Gradle module (build.gradle.kts + Spring Boot entrypoint + hexagonal package skeleton) | BE | P1 | INTACT |
| EDGE-002 | Define DispositivoEdge JPA aggregate root with pairing state machine and exclusive-pairing partial UNIQUE index | BE | P1 | INTACT |
| EDGE-003 | Define DispositivoEnrollment, DispositivoCredential, DispositivoEdgeTransition JPA entities + port interfaces | BE | P1 | INTACT |
| EDGE-004 | Implement REST controllers skeleton with DTOs (DeviceController, HeartbeatController, LivenessController, PairingController) | BE | P1 | INTACT |
| EDGE-005 | Implement DeviceRegistrationService.register() — mint one-time enrollment token (hash-only), respond 201 with plain token | BE | P2 | INTACT |
| EDGE-006 | Implement DeviceRegistrationService.enroll() — exchange enrollment token for OAuth2 client_credentials, return credential once | BE | P2 | INTACT |
| EDGE-007 | Implement PairingService.pair() and unpair() with exclusive-pairing enforcement, state machine, and audit transitions | BE | P2 | INTACT |
| EDGE-008 | Implement HeartbeatService.recordHeartbeat() — server-stamp ultimaSincronizacao, call DetectionProfilePort, raise domain events, detect liveness crossing | BE | P2 | INTACT |
| EDGE-009 | Implement LivenessService.evaluate() — read-time liveness derivation, liveness crossing detection, and liveness endpoints | BE | P2 | INTACT |
| EDGE-010 | Implement JPA adapters for all four EDGE repository ports (DispositivoEdge, Enrollment, Credential, Transition) with multi-tenant schema routing | BE | P3 | INTACT |
| EDGE-011 | Generate Flyway migration V0700__edge_initial_schema.sql (4 tables, pgcrypto extension, indexes, constraints) and contribute to MIG | BE | P3 | INTACT |
| EDGE-012 | Configure OAuth2 JWT resource server (jwk-set-uri via ACS/REG, scope enforcement: edge.read / edge.manage / edge.device / edge.internal) | BE | P5 | INTACT |
| EDGE-013 | Implement TenantContextHolder propagation, MDC enrichment (tenantId, correlationId, deviceId) and X-Correlation-Id filter | BE | P5 | INTACT |
| EDGE-014 | Implement DetectionProfileClient Feign adapter (GET /gest/detection-profiles/active) with Resilience4j circuit breaker and degraded fallback | BE | P4 | INTACT |
| EDGE-015 | Implement DeviceRegistrationService.retire() with credential revocation and state-machine enforcement | BE | P4 | INTACT |
| EDGE-016 | Implement Spring TransactionalEventListener wiring for all 7 in-process domain events (post-commit, DispositivoRegistrado .. ProfileDriftDetected) | BE | P6 | INTACT |
| EDGE-017 | Expose Actuator health/readiness/liveness endpoints and Micrometer metrics (heartbeats.received, pairing.transitions, profile.drift) | BE | P7 | INTACT |
| EDGE-018 | Implement structured JSON logging with MDC (correlationId, tenantId, actor, deviceId) — assert zero biometric/PII/credential in logs | BE | P7 | INTACT |
| EDGE-019 | Wire Cucumber integration test infrastructure (Testcontainers PostgreSQL, Spring Security test stubs, device-credential mock, tenant schema setup) | BE | P8 | INTACT |
| EDGE-020 | Implement full BDD feature coverage — wire all 4 Gherkin features (~38 scenarios: device-registration-enrollment, device-pairing, heartbeat-liveness, profile-pointer-drift) | BE | P8 | INTACT |
| EDGE-021 | Implement optimistic-lock row_version enforcement (409 on stale write), Device detail endpoint (GET /devices/{id}), and DeviceListService with pagination | BE | P9 | INTACT |
| EDGE-INT-001 | Integration with GEST — DetectionProfile version pointer (Feign client, circuit breaker, degraded heartbeat fallback) | INT | P4 | INTACT |
| EDGE-INT-002 | Integration with ACS — OAuth2 JWT validation (jwk-set-uri) and per-device client_credentials registration coordination | INT | P5 | INTACT |
| EDGE-INT-003 | Integration with GWY — upstream route /api/v1/edge/** + enrollment route without Bearer + X-Correlation-Id forwarding | INT | P4 | INTACT |
| EDGE-INT-004 | Integration with REG — Eureka service discovery registration and graceful deregistration | INT | P4 | INTACT |
| EDGE-INT-005 | Integration with MIG — contribute EDGE DDL (V0700..V0799) to centralized migration service | INT | P3 | INTACT |
| EDGE-INT-006 | Integration with PAC — provide EdgeDevicePort (GET /patients/{id}/device and GET /devices/{id}) for device validation | INT | P4 | INTACT |
| EDGE-INT-007 | Integration with DASH — provide liveness poll endpoints (ETag/304, serverTime in response body) for caregiver connectivity signal | INT | P4 | INTACT |

---

## By Priority

| Priority | Issues |
|---|---|
| P1 | EDGE-001, EDGE-002, EDGE-003, EDGE-004 |
| P2 | EDGE-005, EDGE-006, EDGE-007, EDGE-008, EDGE-009 |
| P3 | EDGE-010, EDGE-011, EDGE-INT-005 |
| P4 | EDGE-014, EDGE-015, EDGE-INT-001, EDGE-INT-003, EDGE-INT-004, EDGE-INT-006, EDGE-INT-007 |
| P5 | EDGE-012, EDGE-013, EDGE-INT-002 |
| P6 | EDGE-016 |
| P7 | EDGE-017, EDGE-018 |
| P8 | EDGE-019, EDGE-020 |
| P9 | EDGE-021 |

---

## By SPEC

| SPEC | Issues |
|---|---|
| SPEC-01 (api-surface) | EDGE-004, EDGE-021, EDGE-INT-003, EDGE-INT-006 |
| SPEC-02 (heartbeat-liveness) | EDGE-008, EDGE-009, EDGE-INT-007 |
| SPEC-03 (pairing-lifecycle) | EDGE-002, EDGE-007, EDGE-009, EDGE-015 |
| SPEC-04 (data-model-migrations) | EDGE-002, EDGE-003, EDGE-010, EDGE-011, EDGE-INT-005 |
| SPEC-05 (idempotency-concurrency) | EDGE-003, EDGE-006, EDGE-021 |
| SPEC-06 (security) | EDGE-003, EDGE-006, EDGE-012, EDGE-015, EDGE-INT-002 |
| SPEC-07 (observability) | EDGE-013, EDGE-017, EDGE-018 |
| SPEC-08 (bdd-scenarios) | EDGE-019, EDGE-020 |
| SPEC-09 (messaging) | EDGE-016 |
| SPEC-10 (integrations) | EDGE-008, EDGE-014, EDGE-INT-001 |
| SPEC-11 (registration-enrollment) | EDGE-005, EDGE-006 |
| hub-integration.md | EDGE-INT-002, EDGE-INT-003, EDGE-INT-004, EDGE-INT-005 |
| integration-contracts.md | EDGE-INT-001, EDGE-INT-006, EDGE-INT-007 |

---

## By Hexagonal Layer

| Layer | Issues |
|---|---|
| `api` (controllers + DTOs) | EDGE-004, EDGE-021 |
| `application` (services + listeners) | EDGE-005, EDGE-006, EDGE-007, EDGE-008, EDGE-009, EDGE-013, EDGE-015, EDGE-016 |
| `domain` (model + ports + events) | EDGE-002, EDGE-003 |
| `infrastructure` (persistence + integration + config + metrics) | EDGE-010, EDGE-011, EDGE-012, EDGE-013, EDGE-014, EDGE-017, EDGE-018 |
| cross-cutting (scaffold + BDD) | EDGE-001, EDGE-019, EDGE-020 |
| integration | EDGE-INT-001..007 |
