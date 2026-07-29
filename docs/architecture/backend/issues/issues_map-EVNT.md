# Issues Map -- issues

| Issue | Title | Kind | Priority | Status |
|---|---|---|---|---|
| EVNT-001 | Scaffold Gradle project evnt-service (hexagonal layout + port interfaces) | BE | P1 | INTACT |
| EVNT-002 | Implement ReportIngressController (POST /reports) + biometric-payload guard | BE | P1 | INTACT |
| EVNT-003 | Implement EventQueryController (GET /events, /events/{id}, /events/active) + DTOs | BE | P1 | INTACT |
| EVNT-004 | Implement ConfirmationController (POST /events/{id}/confirmation + dispatches/{id}/ack) | BE | P1 | INTACT |
| EVNT-005 | Implement GlobalExceptionHandler (RFC 7807 ProblemDetail + correlationId on all errors) | BE | P1 | INTACT |
| EVNT-006 | Implement IngestRecognizedReport + GenerateEvento use cases (idempotency check -> GEST validate -> persist Evento + EventoCriado) | BE | P2 | INTACT |
| EVNT-007 | Implement OrchestrateNotification use case (EventoCriado listener, tier derivation, dispatch creation, fail-open retry) | BE | P2 | INTACT |
| EVNT-008 | Implement ConfirmEvento use case (terminal feedback, ACS confirmador resolve, HIST append, EventoConfirmado/FalsoPositivoMarcado) | BE | P2 | INTACT |
| EVNT-009 | Implement boot-time dispatch retry sweep (recover queued/failed dispatches on startup) | BE | P2 | INTACT |
| EVNT-010 | Create JPA entities + adapters (Evento, NotificationDispatch, ReportIdempotency + repositories) | BE | P3 | INTACT |
| EVNT-011 | Create Flyway migration scripts V1..V4 (extensions, evento, notification_dispatch, report_idempotency) | BE | P3 | INTACT |
| EVNT-012 | Configure TenantDataSourceRouter + TenantContextHolder (schema-per-empresa, ADR-003) | BE | P3 | INTACT |
| EVNT-013 | Implement SseEmitterAdapter (SseEventBroadcastPort, fan-out on EventoCriado/EventoConfirmado per tenant) | BE | P4 | INTACT |
| EVNT-014 | Implement Last-Event-ID replay (seq-based resume, keepalive scheduler at keepAliveSeconds interval) | BE | P4 | INTACT |
| EVNT-015 | Implement GestFeignAdapter (GestValidationPort, fail-closed on emergency timeout, 422/502 mapping) | BE | P5 | INTACT |
| EVNT-016 | Implement PacFeignAdapter (CommandLookupPort, snapshot resolve, 422 on deleted Comando) | BE | P5 | INTACT |
| EVNT-017 | Implement AcsFeignAdapter (CaregiverRoutingPort, routing + confirmador resolve) + DashFeignAdapter (fail-open delivery) + HistFeignAdapter (retryable audit) | BE | P5 | INTACT |
| EVNT-018 | Configure OAuth2 resource server + scope enforcement (evnt:read, evnt:confirm on caregiver endpoints) | BE | P6 | INTACT |
| EVNT-019 | Implement device-token validation filter (interim JWT-shaped token: deviceId, tenant, paciente claims; SPEC-06 interim contract) | BE | P6 | INTACT |
| EVNT-020 | Configure Actuator health endpoints (liveness /health/liveness, readiness /health/readiness) | BE | P7 | INTACT |
| EVNT-021 | Configure Micrometer metrics (ingress counter, dispatch latency, false-positive rate gauge) + MDC structured logging | BE | P7 | INTACT |
| EVNT-022 | Implement BDD Cucumber: ingress golden path (emergency full_screen_alarm + routine discreet dispatch) | BE | P8 | INTACT |
| EVNT-023 | Implement BDD Cucumber: idempotent ingress (duplicate reportId 200 OK) + terminal confirmation (409 on re-verdict) | BE | P8 | INTACT |
| EVNT-024 | Implement BDD Cucumber: SSE stream + Last-Event-ID replay + false-positive rate aggregate (GET /events?groupBy=comando) | BE | P8 | INTACT |
| EVNT-025 | Hardening: Resilience4j circuit breakers per Feign client, retry budget exhaustion observability, LGPD biometric guard audit log | BE | P9 | INTACT |
| INT-EVNT-001 | Freeze device-token auth contract with EDGE (gap #1: mTLS vs OAuth2 client_credentials, interim JWT-shaped token boundary) | BE | P1 | INTACT |
| INT-EVNT-002 | Contract tests EVNT -> GEST (WireMock: validation success + 422 reject + 502 fail-closed on emergency timeout) | BE | P5 | INTACT |
| INT-EVNT-003 | Contract tests EVNT -> PAC (WireMock: CommandLookupPort resolve + 422 on deleted/deactivated Comando) | BE | P5 | INTACT |
| INT-EVNT-004 | Contract tests EVNT -> ACS + define emergency escalation protocol (gap #3: secondary-caregiver fallback when primary unroutable) | BE | P5 | INTACT |
| INT-EVNT-005 | Contract tests EVNT -> DASH (WireMock: NotificationDispatchPort fail-open, delivery ack, retry on 5xx) | BE | P5 | INTACT |
| INT-EVNT-006 | Contract tests EVNT -> HIST (WireMock: AuditTrailPort EventoCriado/EventoConfirmado/FalsoPositivoMarcado fact shapes) | BE | P8 | INTACT |
| INT-EVNT-007 | Define false-positive rate consumer interface with PAC (gap #5: sensitivity tuning API - PAC reads /events?groupBy=comando to adjust sensibilidade) | BE | P9 | INTACT |
