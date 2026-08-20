package com.neuroassist.acs.infrastructure.clients;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for pac-service patient resolution. RULE-06: @FeignClient only in
 * infrastructure.clients. Timeout: 2s (fail-closed) — configured in
 * FeignClientConfig/application.yml.
 */
@FeignClient(name = "pac-service", url = "${neuroassist.pac.url:}")
public interface PacPatientResolveClient {

    /**
     * Resolves patient identity from pac-service.
     *
     * @param id patient UUID (path variable)
     * @param tenantId tenant UUID for scoping (query param)
     * @return patient response DTO
     */
    @GetMapping("/api/v1/pac/patients/{id}")
    PatientResponse getPatient(@PathVariable UUID id, @RequestParam UUID tenantId);

    /**
     * Patient response from pac-service. Only safe fields — no PII beyond what is needed for
     * routing.
     *
     * @param id patient UUID
     * @param nome patient name
     * @param tenantId tenant UUID
     * @param ativo whether the patient is active
     */
    record PatientResponse(UUID id, String nome, UUID tenantId, boolean ativo) {}
}
