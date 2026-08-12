package com.neuroassist.gest.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable versioned detection profile pulled by the edge (invariants #2, #3). ZERO Spring / ZERO
 * JPA (RULE-01, RULE-02). Once published, no content fields may be mutated; only {@code active} can
 * be flipped to false by a subsequent publish.
 *
 * <p>Schema: version is monotonically increasing per tenant (ensured by
 * DetectionProfileRepositoryPort.nextVersion()). Only one profile may be active=true at a time
 * (enforced by the partial unique index in the DB).
 */
public record DetectionProfile(
        UUID id,
        long version,
        boolean active,
        String contractVersion,
        Map<String, Object> classificationParams,
        Instant publishedAt,
        String publishedBy,
        String tenantId) {

    /**
     * Factory: create a new active DetectionProfile. Validates that {@code supported} is non-empty
     * (invariant: empty-profile). Snapshot creation (DetectionProfilePrimitive list) is delegated
     * to DetectionProfileDomainService.createSnapshot().
     *
     * @param version next monotonic version from DetectionProfileRepositoryPort.nextVersion()
     * @param contractVersion frozen contract version string (e.g. "v1.0.0")
     * @param classificationParams detection parameters (confidenceThreshold, minWindowMs, etc.)
     * @param supported list of currently SUPPORTED primitives (must be non-empty)
     * @param tenantId tenant context at publish time
     * @param publishedBy actor performing the publish
     */
    public static DetectionProfile publish(
            long version,
            String contractVersion,
            Map<String, Object> classificationParams,
            List<TipoGesto> supported,
            String tenantId,
            String publishedBy) {
        if (supported == null || supported.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot publish a DetectionProfile with no SUPPORTED primitives (invariant: empty-profile)");
        }
        return new DetectionProfile(
                UUID.randomUUID(),
                version,
                true,
                contractVersion,
                classificationParams != null ? classificationParams : Map.of(),
                Instant.now(),
                publishedBy,
                tenantId);
    }
}
