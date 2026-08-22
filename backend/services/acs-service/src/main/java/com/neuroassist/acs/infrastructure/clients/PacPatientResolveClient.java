package com.neuroassist.acs.infrastructure.clients;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for pac-service patient resolution. RULE-06: @FeignClient only in
 * infrastructure.clients. Timeout: 2s (fail-closed) — configured in
 * FeignClientConfig/application.yml.
 *
 * <p><b>The path and the response shape were both wrong</b> until the Sessão 4 of the E2E plan:
 * {@code GET /api/v1/pac/patients/&#123;id&#125;} with a {@code tenantId} query parameter, against
 * a service whose controller is {@code /api/pac/v1/pacientes} and which takes the tenant from the
 * JWT. Every caregiver-patient link creation therefore answered 422 {@code
 * acs.link.unknown-patient} — and the ACS directory, which reads the links, stayed empty, so an
 * alarm had no destination. Nothing caught it because {@code PacPatientResolveAdapter} maps any
 * failure to {@code Optional.empty()}, which is indistinguishable from "the patient really does not
 * exist". This mirrors defects C-4, C-5, C-8, C-9 and C-22 of the same catalogue.
 */
@FeignClient(
        name = "pac-service",
        url = "${neuroassist.pac.url:${PAC_SERVICE_URL:http://pac-service:8082}}")
public interface PacPatientResolveClient {

    /**
     * Resolves patient identity from pac-service.
     *
     * <p>pac-service scopes the read by the tenant of the caller's JWT and the {@code X-Empresa-Id}
     * header, not by a query parameter — the {@code tenantId} argument is kept in the signature
     * because callers pass it, but it is deliberately not sent.
     *
     * @param id patient UUID (path variable)
     * @return patient response DTO
     */
    @GetMapping("/api/pac/v1/pacientes/{id}")
    PatientResponse getPatient(@PathVariable UUID id);

    /**
     * Subset of {@code com.neuroassist.pac.api.dto.PacienteResponse} — mirrored, not invented.
     * pac-service returns no {@code tenantId} (the row is already in the tenant's schema) and no
     * {@code ativo} flag; the previous shape had both and neither ever arrived.
     *
     * @param id patient UUID
     * @param nome patient name
     * @param dispositivoEdgeId paired device, when there is one
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record PatientResponse(UUID id, String nome, UUID dispositivoEdgeId) {}
}
