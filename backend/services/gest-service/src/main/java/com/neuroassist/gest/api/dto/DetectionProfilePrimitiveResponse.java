package com.neuroassist.gest.api.dto;

import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import com.neuroassist.gest.domain.model.Sensibilidade;
import java.util.UUID;

/**
 * Um gesto ativo no perfil, e a faixa de sensibilidade com que detectá-lo.
 *
 * <p>{@code engineFeatureRef} é o que o agente casa com seu detector; {@code sensibilidade} é qual
 * das três faixas de {@link com.neuroassist.gest.domain.model.DetectionThresholdTable} aplicar.
 *
 * <p>{@code origem} diz DE ONDE a faixa veio. Sem ela, "este paciente usa media porque a cuidadora
 * escolheu media" e "este paciente usa media porque ninguém calibrou este gesto" seriam
 * indistinguíveis — a mesma confusão que a {@code ProfilePointer} de três estados resolveu no
 * EDGE, e a razão pela qual HB-04 não passava.
 */
public record DetectionProfilePrimitiveResponse(
        UUID tipoGestoId,
        String nome,
        String engineFeatureRef,
        Sensibilidade sensibilidade,
        Origem origem) {

    /** De onde saiu a faixa deste gesto. */
    public enum Origem {
        /** Da calibração deste paciente — um {@code comando} publicado aponta para este gesto. */
        PACIENTE,
        /** Ninguém calibrou este gesto para este paciente; vale a faixa do perfil da empresa. */
        PERFIL_EMPRESA,
        /** O catálogo do paciente não pôde ser lido. Degradado, e não silenciosamente. */
        INDISPONIVEL
    }

    /** Projeção sem paciente: a faixa é a do perfil da empresa, como publicada. */
    public static DetectionProfilePrimitiveResponse from(DetectionProfilePrimitive p) {
        return new DetectionProfilePrimitiveResponse(
                p.tipoGestoId(),
                p.nomeSnapshot(),
                p.engineFeatureRef(),
                p.sensibilidade(),
                Origem.PERFIL_EMPRESA);
    }

    /** Projeção com a faixa calibrada para o paciente. */
    public static DetectionProfilePrimitiveResponse forPatient(
            DetectionProfilePrimitive p, Sensibilidade doPaciente, Origem origem) {
        return new DetectionProfilePrimitiveResponse(
                p.tipoGestoId(),
                p.nomeSnapshot(),
                p.engineFeatureRef(),
                doPaciente != null ? doPaciente : p.sensibilidade(),
                origem);
    }
}
