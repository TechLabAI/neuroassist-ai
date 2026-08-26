package com.neuroassist.acs.infrastructure.clients;

import com.neuroassist.acs.application.port.out.PacPatientResolvePort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing PacPatientResolvePort via Feign client (pac-service). Timeout: 2s configured
 * in application.yml (feign.client.config.pac-service.readTimeout). Fail mode: fail-closed — empty
 * Optional on any error (patient not found → 404 by caller). RULE-06: @FeignClient is in
 * PacPatientResolveClient (infrastructure.clients).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PacPatientResolveAdapter implements PacPatientResolvePort {

    private final PacPatientResolveClient pacClient;

    @Override
    public Optional<PatientResolveResponse> resolvePatient(UUID pacienteId, UUID tenantId) {
        try {
            PacPatientResolveClient.PatientResponse response = pacClient.getPatient(pacienteId);
            if (response == null) {
                return Optional.empty();
            }
            // pac-service has no "inactive patient" state and returns no tenantId — the row lives
            // in the tenant's schema, so reaching it at all proves the tenant. The previous shape
            // read both fields from a response that never carried them.
            return Optional.of(
                    new PatientResolveResponse(response.id(), tenantId, response.nome(), "ACTIVE"));
        } catch (feign.FeignException.NotFound e) {
            log.debug(
                    "Patient not found in pac-service: pacienteId={} tenantId={}",
                    pacienteId,
                    tenantId);
            return Optional.empty();
        } catch (Exception e) {
            log.warn(
                    "Failed to resolve patient from pac-service: pacienteId={} tenantId={} error={}",
                    pacienteId,
                    tenantId,
                    e.getMessage());
            return Optional.empty();
        }
    }
}
