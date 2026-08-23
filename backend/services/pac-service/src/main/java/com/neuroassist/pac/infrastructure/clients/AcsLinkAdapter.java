package com.neuroassist.pac.infrastructure.clients;

import com.neuroassist.pac.application.port.out.AcsLinkPort;
import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter for {@link AcsLinkPort} over {@link AcsLinkClient} (E2E-015).
 *
 * <p><b>It classifies failures instead of collapsing them.</b> A 4xx from acs-service is a refusal
 * that will repeat identically — a 409 because this is the patient's last active caregiver (D-204),
 * a 422 because the patient is unknown there, a 403 because pac-service lacks the authority.
 * Retrying any of those forever only hides them behind a growing counter. Anything else — timeout,
 * connection refused, 5xx — is transient and deserves the next tick.
 *
 * <p>This distinction is the lesson from the EDGE heartbeat: a fallback that turned a 403 into
 * "GEST is degraded" made an authorization bug indistinguishable from an outage, and it stayed
 * invisible for a whole session.
 *
 * <p>Never throws: the drain loop decides what an outcome means, and an exception escaping here
 * would abort a batch with some entries already marked sent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AcsLinkAdapter implements AcsLinkPort {

    private final AcsLinkClient acsLinkClient;

    @Override
    public Outcome link(UUID pacienteId, UUID cuidadorId) {
        try {
            // RULE-07 — the ACS cross-checks this against the tenant claim of the service token.
            acsLinkClient.createLink(cuidadorId, pacienteId, TenantContextHolder.require());
            return Outcome.ACCEPTED;
        } catch (Exception e) {
            return classify(e, "link", pacienteId, cuidadorId);
        }
    }

    @Override
    public Outcome unlink(UUID pacienteId, UUID cuidadorId) {
        try {
            acsLinkClient.revokeLink(cuidadorId, pacienteId);
            return Outcome.ACCEPTED;
        } catch (Exception e) {
            return classify(e, "unlink", pacienteId, cuidadorId);
        }
    }

    private Outcome classify(Exception e, String operation, UUID pacienteId, UUID cuidadorId) {
        if (e instanceof feign.FeignException fe && fe.status() >= 400 && fe.status() < 500) {
            log.error(
                    "acs-service refused {} paciente={} cuidador={} status={} — not retryable: {}",
                    operation,
                    pacienteId,
                    cuidadorId,
                    fe.status(),
                    fe.getMessage());
            return Outcome.REFUSED;
        }
        log.warn(
                "acs-service unavailable for {} paciente={} cuidador={}: {}",
                operation,
                pacienteId,
                cuidadorId,
                e.getMessage());
        return Outcome.RETRY;
    }
}
