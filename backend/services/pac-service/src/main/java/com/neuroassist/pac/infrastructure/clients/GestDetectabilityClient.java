package com.neuroassist.pac.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * INT-PAC-001 — Feign client for GEST gesture detectability checks (SPEC-10 §4).
 *
 * <p>RULE-06: @FeignClient only in {@code infrastructure.clients}. Service discovery via Eureka +
 * Spring Cloud LoadBalancer (name = "gest-service"). Header propagation (X-Correlation-Id,
 * X-Empresa-Id, Authorization) handled globally by {@link
 * com.neuroassist.pac.infrastructure.config.FeignClientConfig}.
 *
 * <p>Resilience: circuit breaker "gest-service" is applied programmatically by {@link
 * EmergencyEligibilityGateImpl}. Connect timeout 2s / read timeout 3s (via Feign config). Timeout
 * is intentional — a 2.5s response from GEST during emergency publish triggers the CB and emits a
 * 503 (fail-closed ADR-005).
 *
 * <p>The {@code url} property is optional for dev overrides; in production, Eureka resolves the
 * instance. Use {@code neuroassist.integration.gest.url=} (empty) for Eureka-only routing.
 */
@FeignClient(
        name = "gest-service",
        url = "${neuroassist.integration.gest.url:http://gest-service:8084}")
public interface GestDetectabilityClient {

    /**
     * Checks whether a gesture primitive combination is detectable by the GEST ML model.
     *
     * @param request carries primitivoPrincipalId and optional primitivoSecundarioId
     * @return detectability result — {@code supported=true} means the gesture is ready
     * @throws feign.FeignException on HTTP 4xx/5xx or network failure; caller handles resilience
     */
    @PostMapping("/api/gest/v1/detectability/check")
    GestDetectabilityResponse checkDetectability(@RequestBody GestDetectabilityRequest request);
}
