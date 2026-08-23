package com.neuroassist.pac.infrastructure.clients;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * E2E-015 — Feign client for the ACS caregiver-patient link surface.
 *
 * <p>RULE-06: {@code @FeignClient} declared only in {@code infrastructure.clients}.
 *
 * <p><b>Mirrored from the producer, not from an assumption.</b> Path, verb and the {@code tenantId}
 * query parameter come from {@code CaregiverPatientLinkController} in acs-service, read directly.
 * Five of the sixteen cross-service defects in this project (C-4, C-5, C-8, C-9, C-22) were a
 * consumer inventing a producer's contract and then writing a WireMock stub that agreed with the
 * invention — which makes the test pass exactly when the assumption is wrong.
 *
 * <p><b>Why the response is discarded.</b> The controller answers {@code
 * CaregiverPatientLinkResponse}, but the drain only needs to know whether the ACS accepted. Binding
 * the body would add a shape to keep in sync for no gain; the status code carries everything.
 *
 * <p><b>Service identity.</b> {@link AcsLinkFeignConfig} swaps the {@code Authorization} header for
 * pac-service's own token. Unlike {@code AcsDirectoryClient}, this client is driven by a scheduler:
 * there is no caller whose token could be propagated.
 */
@FeignClient(
        name = "acs-service-link",
        url = "${neuroassist.integration.acs.url:http://acs-service:8088}",
        configuration = AcsLinkFeignConfig.class)
public interface AcsLinkClient {

    /**
     * Creates the link. Idempotent: an existing active link answers 200 instead of 201.
     *
     * <p>The ACS validates that {@code tenantId} matches the tenant claim of the token — a mismatch
     * is a 4xx, not a retryable failure.
     *
     * <p><b>{@code tenantId} is a {@code String} here although the controller declares a {@code
     * UUID}.</b> Inside pac-service the tenant is a {@code String} everywhere ({@code
     * TenantContextHolder}, {@code Paciente.tenantId}); parsing it to a {@code UUID} just to
     * serialise it back to text would add a failure mode the rest of the service does not have, and
     * one that behaves differently in the test suite — whose tenants are aliases like {@code
     * empresa-abc} — than in production, where they are UUIDs. Sending the identifier verbatim
     * keeps the two environments on the same path; a value the ACS cannot parse is a 400 there,
     * which the adapter already classifies as a terminal refusal.
     */
    @PostMapping("/api/v1/acs/caregivers/{cuidadorId}/patients/{pacienteId}/link")
    void createLink(
            @PathVariable("cuidadorId") UUID cuidadorId,
            @PathVariable("pacienteId") UUID pacienteId,
            @RequestParam("tenantId") String tenantId);

    /**
     * Revokes the link.
     *
     * <p>Answers 409 when this is the patient's last active caregiver — the ACS coverage invariant
     * (D-204). That is a refusal, not an outage: see {@code AcsLinkPort.Outcome}.
     */
    @DeleteMapping("/api/v1/acs/caregivers/{cuidadorId}/patients/{pacienteId}/link")
    void revokeLink(
            @PathVariable("cuidadorId") UUID cuidadorId,
            @PathVariable("pacienteId") UUID pacienteId);
}
