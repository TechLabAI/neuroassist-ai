package com.neuroassist.pac.infrastructure.clients;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
     * Checks whether the tenant's gesture primitives satisfy the emergency eligibility invariant.
     * GEST evaluates the urgency level: for EMERGENCIA all registered primitives must be SUPPORTED;
     * for other levels always returns eligible=true.
     *
     * @param request carries the urgencyLevel string (e.g. "EMERGENCIA", "ROTINA")
     * @return eligibility result — {@code eligible=true} means the command may be published
     * @throws feign.FeignException on HTTP 4xx/5xx or network failure; caller handles resilience
     */
    @PostMapping("/gest-service/eligibility")
    GestDetectabilityResponse checkDetectability(@RequestBody GestDetectabilityRequest request);

    /**
     * Resolves a single gesture primitive by id. The eligibility endpoint above evaluates the
     * tenant's primitive set as a whole and cannot answer "is THIS primitive known", so the
     * per-primitive guard on {@code :validate} uses this lookup instead (E2E-009).
     *
     * @param id the primitive id referenced by a Comando
     * @throws feign.FeignException.NotFound when GEST does not know the primitive
     */
    @GetMapping("/gest-service/tipo-gesto/{id}")
    void getPrimitive(@PathVariable("id") UUID id);
}
