package com.neuroassist.gest.application.service;

import com.neuroassist.gest.api.dto.EligibilityRequest;
import com.neuroassist.gest.api.dto.EligibilityResponse;
import com.neuroassist.gest.application.port.in.EligibilityUseCase;
import com.neuroassist.gest.application.port.out.TipoGestoRepositoryPort;
import com.neuroassist.gest.domain.model.DetectabilityState;
import com.neuroassist.gest.domain.model.TipoGesto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Eligibility guard invoked by PAC for emergency-command dispatch (GEST-008). Stateless read — no
 * writes, no events. Tenant isolation is implicit via the repository port's schema routing.
 *
 * <p>ADR-006 invariant: an EMERGENCIA command may only be dispatched when ALL registered primitives
 * are in SUPPORTED state. Any other urgency level is always eligible.
 *
 * <p>NOTE — RULE-07: TenantContextHolder.require() is deferred to P5, when TenantContextFilter will
 * populate the holder from the JWT. The repository port already isolates data per tenant via schema
 * routing set by the filter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EligibilityService implements EligibilityUseCase {

    private final TipoGestoRepositoryPort tipoGestoRepositoryPort;

    /**
     * Checks whether the current tenant's gesture primitives satisfy the ADR-006 eligibility
     * invariant for the requested urgency level.
     *
     * <p>For {@code EMERGENCIA}: all primitives must be in {@code SUPPORTED} state. Any primitive
     * not in {@code SUPPORTED} is reported in {@code missingStates}. For any other urgency level:
     * always eligible.
     */
    @Override
    @Transactional(readOnly = true)
    public EligibilityResponse check(EligibilityRequest request) {
        String urgencyLevel = request.urgencyLevel();

        if ("EMERGENCIA".equalsIgnoreCase(urgencyLevel)) {
            List<TipoGesto> all = tipoGestoRepositoryPort.findAllByTenant();
            List<String> missingStates =
                    all.stream()
                            .filter(tg -> tg.detectabilityState() != DetectabilityState.SUPPORTED)
                            .map(tg -> tg.nome() + ":" + tg.detectabilityState().name())
                            .toList();
            boolean eligible = missingStates.isEmpty();
            log.info(
                    "Eligibility check urgency={} eligible={} notSupportedCount={}",
                    urgencyLevel,
                    eligible,
                    missingStates.size());
            return new EligibilityResponse(eligible, missingStates);
        }

        // Non-EMERGENCIA urgency levels are always eligible (no primitives restriction)
        log.debug(
                "Eligibility check urgency={}: non-emergency, returning eligible=true",
                urgencyLevel);
        return new EligibilityResponse(true, List.of());
    }
}
