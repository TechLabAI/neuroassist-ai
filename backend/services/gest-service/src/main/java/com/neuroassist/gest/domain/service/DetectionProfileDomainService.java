package com.neuroassist.gest.domain.service;

import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import com.neuroassist.gest.domain.model.TipoGesto;
import java.util.List;
import java.util.UUID;

/**
 * Domain service for DetectionProfile invariants. Pure POJO — zero Spring imports (RULE-01).
 * Registered as @Bean in DomainServiceConfig.
 *
 * <p>Snapshot invariant: only SUPPORTED primitives (engineFeatureRef non-null) may be included in a
 * published profile. Validated by DetectionProfilePrimitive.fromSupported().
 */
public class DetectionProfileDomainService {

    /**
     * Creates an immutable snapshot of the SUPPORTED primitives for a newly published profile. Each
     * primitive must have a non-null engineFeatureRef (enforced by
     * DetectionProfilePrimitive.fromSupported()).
     *
     * @param supported list of SUPPORTED TipoGesto primitives (must be non-empty — caller
     *     validates)
     * @param profileId id of the newly created DetectionProfile
     * @return ordered list of DetectionProfilePrimitive snapshots
     */
    public List<DetectionProfilePrimitive> createSnapshot(
            List<TipoGesto> supported, UUID profileId) {
        return supported.stream()
                .map(tg -> DetectionProfilePrimitive.fromSupported(profileId, tg))
                .toList();
    }
}
