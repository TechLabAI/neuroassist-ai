package com.neuroassist.gest.application.port.out;

import com.neuroassist.gest.domain.model.DetectionProfile;
import java.util.Optional;

/**
 * Outbound port — DetectionProfile persistence. Profiles are immutable after publish; only the
 * {@code active} flag can be flipped (via {@link #deactivateCurrent()}). Multi-tenancy is implicit
 * via schema routing. Implemented by DetectionProfileRepositoryAdapter (infrastructure layer, P3).
 */
public interface DetectionProfileRepositoryPort {

    DetectionProfile save(DetectionProfile profile);

    Optional<DetectionProfile> findActive();

    /**
     * Returns the next monotonic version number for this tenant's profile sequence. The first
     * version is 1 ({@code COALESCE(MAX(version), 0) + 1}). Must be called inside an active
     * transaction (RULE-03 — @Transactional only in application.service).
     */
    long nextVersion();

    /**
     * Marks all currently-active profiles for this tenant as inactive. Called before persisting a
     * new profile so that at most one profile is active at a time (publish invariant, GEST-009).
     * Must be called inside an active transaction.
     */
    void deactivateCurrent();
}
