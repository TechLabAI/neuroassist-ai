package com.neuroassist.evnt.infrastructure.clients.adapter;

import com.neuroassist.evnt.application.port.out.CaregiverRoutingPort;
import com.neuroassist.evnt.infrastructure.clients.CaregiverRoutingClient;
import feign.FeignException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolves caregivers through the acs-service directory.
 *
 * <p>Replaces {@code CaregiverRoutingHardcodedAdapter}, which returned the literal string {@code
 * "cuidadora-jamilly-fixo"} from production code for any argument.
 *
 * <p><b>Fail-closed.</b> If the directory cannot be reached, an alarm has no known destination —
 * there is no safe default caregiver to invent. The exception propagates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaregiverRoutingFeignAdapter implements CaregiverRoutingPort {

    private final CaregiverRoutingClient client;

    @Override
    public List<ResponsibleCaregiver> responsibleFor(String pacienteId) {
        UUID id = parseUuid(pacienteId, "patient");
        if (id == null) {
            return List.of();
        }
        var response = client.findCaregiversForPatient(id);
        if (response == null || response.responsibleCaregivers() == null) {
            return List.of();
        }
        return response.responsibleCaregivers().stream()
                .map(c -> new ResponsibleCaregiver(String.valueOf(c.cuidadorId()), c.papel()))
                .toList();
    }

    @Override
    public boolean isKnownCaregiver(String caregiverId) {
        UUID id = parseUuid(caregiverId, "caregiver");
        if (id == null) {
            return false;
        }
        try {
            // A caregiver acs-service does not know answers 404; the scope may legitimately be
            // empty for one it does know, so presence — not list size — is the signal.
            return client.findPatientScope(id) != null;
        } catch (FeignException.NotFound e) {
            log.warn("Caregiver {} is unknown to acs-service", id);
            return false;
        }
    }

    private UUID parseUuid(String value, String what) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.warn("Not a valid {} id: {}", what, value);
            return null;
        }
    }
}
