package com.neuroassist.pac.infrastructure.clients;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for ACS directory queries (INT-PAC-002 / SPEC-10 §5).
 *
 * <p>RULE-06: @FeignClient only in {@code infrastructure.clients}. Service discovery via Eureka +
 * Spring Cloud LoadBalancer (name = "acs-service"). Header propagation (X-Correlation-Id,
 * X-Empresa-Id, Authorization) handled globally by {@link
 * com.neuroassist.pac.infrastructure.config.FeignClientConfig} interceptors (INT-PAC-005).
 *
 * <p>Resilience: connect timeout 2s / read timeout 3s (configured in application.yml feign
 * section). 1 retry for idempotent GETs. On ACS unreachable the adapter throws
 * CodedException("acs.unavailable") → HTTP 503.
 */
@FeignClient(
        name = "acs-service",
        url = "${neuroassist.integration.acs.url:http://acs-service:8088}")
public interface AcsDirectoryClient {

    /**
     * Resolves an Empresa by id.
     *
     * @return {@link AcsEmpresaResponse} with id and valid flag
     * @throws feign.FeignException.NotFound (404) if empresa is unknown — caller handles
     */
    @GetMapping("/api/v1/acs/directory/tenants/{id}")
    AcsEmpresaResponse getEmpresa(@PathVariable("id") UUID id);

    /**
     * Resolves a Cuidador (caregiver) by id.
     *
     * @return {@link AcsCuidadorResponse} with id and empresaId
     * @throws feign.FeignException.NotFound (404) if cuidador is unknown — caller handles
     */
    @GetMapping("/api/v1/acs/caregivers/{id}")
    AcsCuidadorResponse getCuidador(@PathVariable("id") UUID id);
}
