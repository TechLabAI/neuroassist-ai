# Issues Map -- GEST

| Issue | Title | Kind | Priority | Status |
|---|---|---|---|---|
| GEST-001 | Create gest-service Gradle module (build.gradle.kts + Spring Boot entrypoint + hexagonal package skeleton) | BE | P1 | INTACT |
| GEST-002 | Define TipoGesto and TipoGestoTransition JPA entities with detectability state check constraints | BE | P1 | INTACT |
| GEST-003 | Define DetectionProfile and DetectionProfilePrimitive JPA entities with monotonic-version and single-active constraints | BE | P1 | INTACT |
| GEST-004 | Define repository and outbound port interfaces (TipoGestoRepositoryPort, DetectionProfileRepositoryPort, EdgePairingPort) | BE | P1 | INTACT |
| GEST-005 | Implement REST controllers skeleton with DTOs (TipoGestoController, EligibilityController, DetectionProfileController, DetectionContractController) | BE | P1 | INTACT |
| GEST-006 | Implement TipoGestoService.register() with duplicate-nome guard, tenant isolation, and PrimitiveRegistered in-process event | BE | P2 | INTACT |
| GEST-007 | Implement TipoGestoService.transition() state-machine enforcement, append-only audit row, PrimitivePromoted and PrimitiveDeprecated events | BE | P2 | INTACT |
| GEST-008 | Implement EligibilityService.check() — emergency guard invariant (all primitives must be supported; rotina allows non-deprecated) | BE | P2 | INTACT |
| GEST-009 | Implement DetectionProfileService.publish() — snapshot supported primitives, monotonic version, deactivate prior active profile | BE | P2 | INTACT |
| GEST-010 | Implement DetectionContractService.getContractDescriptor() and validateReport() — stateless contract validation (no persistence, no biometric data) | BE | P2 | INTACT |
| GEST-011 | Implement JPA adapters for TipoGestoRepositoryPort and TipoGestoTransitionRepository (Spring Data JPA, multi-tenant schema routing) | BE | P3 | INTACT |
| GEST-012 | Implement JPA adapters for DetectionProfileRepositoryPort (Spring Data JPA, monotonic sequence, single-active constraint) | BE | P3 | INTACT |
| GEST-013 | Generate Flyway migration V0400 for GEST DDL (4 tables: tipo_gesto, tipo_gesto_transition, detection_profile, detection_profile_primitive) and contribute to MIG service | BE | P3 | INTACT |
| GEST-014 | Wire multi-tenant SchemaRoutingDataSource for gest-service + 503-on-unresolved-schema guard | BE | P3 | INTACT |
| GEST-015 | Implement EdgePairingClient Feign adapter (GET /api/v1/edge/devices/{deviceId}/tenant via REG discovery) with Resilience4j circuit breaker | BE | P4 | INTACT |
| GEST-016 | Implement conditional-GET (ETag/304) on GET /devices/{deviceId}/detection-profile for edge pull bandwidth optimization | BE | P4 | INTACT |
| GEST-017 | Configure OAuth2 JWT resource server (jwk-set-uri via REG, scope enforcement: gest.read / gest.manage / device credential) | BE | P5 | INTACT |
| GEST-018 | Implement TenantContextHolder propagation + MDC enrichment (tenantId, correlationId, X-Correlation-Id echo) | BE | P5 | INTACT |
| GEST-019 | Implement Spring TransactionalEventListener wiring for Primitive* in-process domain events (post-commit, no rollback on listener failure) | BE | P6 | INTACT |
| GEST-020 | Implement DetectionProfilePublished listener — deactivate prior active profile and invalidate profile cache | BE | P6 | INTACT |
| GEST-021 | Expose Actuator health/readiness/liveness endpoints and Micrometer metrics (profile.versions.published, primitive.transitions.count) | BE | P7 | INTACT |
| GEST-022 | Implement structured JSON logging with MDC (correlationId, tenantId, actor) — assert zero biometric/PII data in any log line | BE | P7 | INTACT |
| GEST-023 | Wire Cucumber integration test infrastructure (Testcontainers PostgreSQL, Spring Security test stubs, tenant schema setup) | BE | P8 | INTACT |
| GEST-024 | Implement full BDD feature coverage — wire all 4 Gherkin features (~54 scenarios: lifecycle, eligibility, detection-profile, report-validation) | BE | P8 | INTACT |
| GEST-025 | Implement Resilience4j circuit breaker on EdgePairingClient (50%/10-call, 30s wait, 503 fallback) and LGPD audit (no biometric in errors/logs/responses) | BE | P9 | INTACT |
| INT-GEST-001 | Integration with EDGE service — device pairing verification for profile pull | INT | P4 | INTACT |
| INT-GEST-002 | Integration with ACS service — OAuth2 JWT validation via jwk-set-uri | INT | P5 | INTACT |
| INT-GEST-003 | Integration with GWY — upstream route /api/v1/gest/** + X-Correlation-Id forwarding | INT | P4 | INTACT |
| INT-GEST-004 | Integration with REG — Eureka service discovery registration + graceful deregistration | INT | P4 | INTACT |
| INT-GEST-005 | Integration with MIG — contribute GEST DDL (V0400..V0499) to centralized migration service | INT | P3 | INTACT |
| INT-GEST-006 | Integration contract with PAC — eligibility check endpoint consumed synchronously | INT | P2 | INTACT |
| INT-GEST-007 | Bridge transport auth joint decision with EDGE team (mTLS vs OAuth2 client_credentials — open item SPEC-06 §3.2) | INT | P4 | INTACT |
