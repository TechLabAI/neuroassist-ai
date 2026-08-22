package com.neuroassist.pac.infrastructure.clients;

import com.neuroassist.pac.application.port.out.HistErasurePort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter for {@link HistErasurePort} over {@link HistErasureClient}.
 *
 * <p>No fallback and no outbox, deliberately. The audit-fact adapter next door absorbs failures
 * into a retry queue because a delayed audit entry is still a correct audit entry; a delayed
 * erasure is a live copy of personal data that the requester was told is gone. The exception
 * propagates and pac-service leaves its own row untouched, so the operation can be retried as a
 * whole instead of ending half-applied.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistErasureAdapter implements HistErasurePort {

    private final HistErasureClient client;

    @Override
    public void erasePatientData(UUID pacienteId, String tenantId) {
        try {
            client.erasePersonalData(pacienteId, tenantId);
            log.info("hist-service erased the encryption key for patient {}", pacienteId);
        } catch (Exception e) {
            throw new HistErasureFailedException(
                    "hist-service did not erase the personal data of patient "
                            + pacienteId
                            + " — the patient row was left untouched so the request can be retried",
                    e);
        }
    }
}
