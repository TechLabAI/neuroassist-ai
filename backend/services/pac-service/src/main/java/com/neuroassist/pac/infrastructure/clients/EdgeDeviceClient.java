package com.neuroassist.pac.infrastructure.clients;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for EDGE device queries (INT-PAC-003 / SPEC-10 §6).
 *
 * <p>RULE-06: @FeignClient only in {@code infrastructure.clients}. Service discovery via Eureka +
 * Spring Cloud LoadBalancer (name = "edge-service"). Header propagation (X-Correlation-Id,
 * X-Empresa-Id, Authorization) handled globally by {@link
 * com.neuroassist.pac.infrastructure.config.FeignClientConfig} interceptors (INT-PAC-005).
 *
 * <p>Resilience: connect timeout 2s / read timeout 3s (application.yml). 1 retry for idempotent
 * GET. On EDGE unreachable the adapter throws CodedException("edge.unavailable") → HTTP 503.
 */
@FeignClient(
        name = "edge-service",
        url = "${neuroassist.integration.edge.url:http://edge-service:8087}")
public interface EdgeDeviceClient {

    /**
     * Resolves an Edge device by id.
     *
     * @return {@link EdgeDispositivoResponse} with id, pacienteId (nullable), empresaId
     * @throws feign.FeignException.NotFound (404) if device is unknown — caller handles
     */
    @GetMapping("/api/v1/edge/devices/{id}")
    EdgeDispositivoResponse getDispositivo(@PathVariable("id") UUID id);
}
