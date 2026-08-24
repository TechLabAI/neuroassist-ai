package com.neuroassist.gest.api.dto;

import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import com.neuroassist.gest.domain.model.Sensibilidade;
import java.util.UUID;

/**
 * Um gesto ativo no perfil, e a faixa de sensibilidade com que detectá-lo.
 *
 * <p>{@code engineFeatureRef} é o que o agente casa com seu detector; {@code sensibilidade} é qual
 * das três faixas de {@link com.neuroassist.gest.domain.model.DetectionThresholdTable} aplicar a
 * este gesto.
 */
public record DetectionProfilePrimitiveResponse(
        UUID tipoGestoId, String nome, String engineFeatureRef, Sensibilidade sensibilidade) {

    public static DetectionProfilePrimitiveResponse from(DetectionProfilePrimitive p) {
        return new DetectionProfilePrimitiveResponse(
                p.tipoGestoId(), p.nomeSnapshot(), p.engineFeatureRef(), p.sensibilidade());
    }
}
