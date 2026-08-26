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
        UUID id,
        UUID profileId,
        UUID tipoGestoId,
        String nomeSnapshot,
        String engineFeatureRef,
        Sensibilidade sensibilidade) {

    public DetectionProfilePrimitive {
        // Nunca nulo na borda: o agente escolhe limiar por primitivo, e um nulo aqui viraria
        // um NullPointerException a 30 fps na casa do paciente.
        sensibilidade = sensibilidade != null ? sensibilidade : Sensibilidade.padrao();
    }

    /**
     * Snapshot de um TipoGesto SUPPORTED para um perfil, na faixa de sensibilidade escolhida.
     *
     * @param sensibilidade faixa aplicada a este gesto; {@code null} adota {@link
     *     Sensibilidade#padrao()}
     */
    public static DetectionProfilePrimitive fromSupported(
            UUID profileId, TipoGesto tipoGesto, Sensibilidade sensibilidade) {
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
                tipoGesto.engineFeatureRef(),
                sensibilidade);
    }
}
