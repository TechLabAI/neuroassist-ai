package com.neuroassist.hist.infrastructure.clients;

import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for ACS actor directory — resolves caregiver→patient scope for caregiver-scoped
 * history reads ({@code hist:read}). Not called during ingestion (ADR-005).
 *
 * <p>Timeout: 2s connect + 2s read via {@code feign.client.config.acs-directory.*} in
 * application.yml. Circuit breaker: Resilience4j {@code acs-directory} instance with 50 % failure
 * threshold and 30 s open window. The tenant-id and bearer token are forwarded automatically by the
 * interceptors in {@link com.neuroassist.hist.infrastructure.config.FeignClientConfig} (RULE-06:
 * Feign only in infrastructure.clients/config).
 */
@FeignClient(
        name = "acs-directory",
        url = "${neuroassist.acs.url:http://localhost:8088}",
        configuration = com.neuroassist.hist.infrastructure.config.FeignClientConfig.class)
public interface AcsActorDirectoryClient {

    /**
     * Returns the UUIDs of patients that the given caregiver is authorised to see. Corresponds to
     * {@code GET /api/v1/acs/caregivers/{caregiverId}/patients} on acs-service.
     */
    @GetMapping("/api/v1/acs/caregivers/{caregiverId}/patients")
    List<UUID> getPatientsByCaregiver(@PathVariable("caregiverId") UUID caregiverId);
}
