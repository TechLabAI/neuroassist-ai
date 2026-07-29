# Issues Map -- GWY

| Issue | Title | Kind | Priority | Status |
|---|---|---|---|---|
| GWY-001 | Bootstrap Spring Cloud Gateway service skeleton (reactive, no datasource) | BE | P1 | INTACT |
| GWY-002 | Configure route predicates for downstream services (lb:// discovery) | BE | P1 | INTACT |
| GWY-003 | Implement RFC 7807 problem-detail error model with correlationId | BE | P1 | INTACT |
| GWY-004 | Implement CorrelationIdFilter (generate/forward/echo X-Correlation-Id) | BE | P2 | INTACT |
| GWY-005 | Wire the normative filter pipeline ordering with explicit getOrder() | BE | P2 | INTACT |
| GWY-006 | Implement TenantContextPropagationFilter (strip inbound, set from principal) | BE | P2 | INTACT |
| GWY-007 | Exclude DataSource auto-configuration and assert zero DataSource bean | BE | P3 | INTACT |
| GWY-008 | Implement JwkClient with TTL cache + stale-while-revalidate against ACS JWK set | BE | P4 | INTACT |
| GWY-009 | Implement JwtAuthenticationFilter resolving the caregiver GatewayPrincipal | BE | P4 | INTACT |
| GWY-010 | Implement DeviceCredentialValidator port + SignedTokenDeviceValidator (ADR-005, swappable) | BE | P4 | INTACT |
| GWY-011 | Wire Eureka discovery client + eurekaRegistration health indicator | BE | P4 | INTACT |
| GWY-012 | Implement CORS policy and gateway-side pre-flight handling | BE | P5 | INTACT |
| GWY-013 | Implement route-level coarse authorization matchers (public/caregiver/device) | BE | P5 | INTACT |
| GWY-014 | Assert zero broker dependency and document synchronous back-pressure substitute | BE | P6 | INTACT |
| GWY-015 | Implement StructuredAccessLogger with MDC correlationId/empresaId (no PII/body) | BE | P7 | INTACT |
| GWY-016 | Implement token-bucket RateLimiter filter with 429 + Retry-After | BE | P7 | INTACT |
| GWY-017 | Expose health/readiness/prometheus/routes actuator endpoints | BE | P7 | INTACT |
| GWY-018 | Implement Cucumber feature routing-and-authentication.feature | BE | P8 | INTACT |
| GWY-019 | Implement Cucumber feature tenant-and-correlation-propagation.feature | BE | P8 | INTACT |
| GWY-020 | Implement Cucumber feature rate-limiting-and-cors.feature | BE | P8 | INTACT |
| GWY-021 | Implement GET-only retry policy against another discovered instance | BE | P9 | INTACT |
| INT-GWY-001 | Integration with Service Registry (Eureka) discovery + readiness DOWN fallback | INT | P4 | INTACT |
| INT-GWY-002 | Integration with ACS JWK/OIDC endpoint (cache TTL + stale-while-revalidate) | INT | P4 | INTACT |
| INT-GWY-003 | Integration bridge to EDGE/ACS device-auth authority (swappable, G-1) | INT | P4 | INTACT |
| INT-GWY-004 | Integration routing to downstream services via lb:// with 502/503/504 mapping | INT | P4 | INTACT |
| INT-GWY-005 | Integration with external LB/DNS health probe (public liveness) | INT | P7 | INTACT |
