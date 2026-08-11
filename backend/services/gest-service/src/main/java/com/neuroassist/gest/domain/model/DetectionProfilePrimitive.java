package com.neuroassist.gest.domain.model;

import java.util.UUID;

/**
 * Snapshot member of a DetectionProfile — captures the nome and engineFeatureRef of a SUPPORTED
 * TipoGesto at the moment of publish. ZERO Spring / ZERO JPA (RULE-01, RULE-02). Immutable after
 * creation.
 *
 * <p>engineFeatureRef is required (NOT NULL in schema) — guaranteed by the SUPPORTED state
 * invariant of TipoGesto (ck_tipo_gesto_supported_needs_ref).
 */
public record DetectionProfilePrimitive(
        UUID id, UUID profileId, UUID tipoGestoId, String nomeSnapshot, String engineFeatureRef) {

    /** Factory: creates a snapshot entry from a SUPPORTED TipoGesto for a given profile. */
    public static DetectionProfilePrimitive fromSupported(UUID profileId, TipoGesto tipoGesto) {
        if (tipoGesto.engineFeatureRef() == null) {
            throw new IllegalStateException(
                    "TipoGesto '"
                            + tipoGesto.nome()
                            + "' is SUPPORTED but has no engineFeatureRef — data integrity violation");
        }
        return new DetectionProfilePrimitive(
                UUID.randomUUID(),
                profileId,
                tipoGesto.id(),
                tipoGesto.nome(),
                tipoGesto.engineFeatureRef());
    }
}
