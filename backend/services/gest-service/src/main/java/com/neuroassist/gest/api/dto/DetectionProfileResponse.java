package com.neuroassist.gest.api.dto;

import com.neuroassist.gest.domain.model.DetectionThresholdTable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Perfil de detecção como o agente de borda o recebe.
 *
 * <p>{@code thresholds} é o vocabulário da empresa — o que "alta" significa numericamente nesta
 * clínica. {@code primitives} diz quais gestos detectar e, quando a resposta é por dispositivo, com
 * que faixa <b>deste paciente</b> (E2E-034). A sensibilidade não é copiada para dentro do perfil
 * publicado: ela vive no {@code comando} do paciente, no PAC, e é projetada na leitura — copiá-la
 * criaria um valor que envelhece no instante em que alguém recalibra.
 *
 * @param pacienteId preenchido só na resposta por dispositivo; {@code null} na resposta da empresa
 * @param catalogoVersao versão do catálogo do paciente que produziu esta projeção; {@code null}
 *     quando não houve projeção ou o catálogo não pôde ser lido
 * @param catalogoDegradado {@code true} SÓ quando o PAC não respondeu — nunca quando ele respondeu
 *     "este paciente não tem catálogo"
 * @param etag na resposta por dispositivo combina as DUAS versões: só {@code version} faria a
 *     recalibração de um paciente não invalidar o cache do dispositivo dele
 */
public record DetectionProfileResponse(
        UUID id,
        long version,
        boolean active,
        String contractVersion,
        DetectionThresholdTable thresholds,
        List<DetectionProfilePrimitiveResponse> primitives,
        UUID pacienteId,
        Integer catalogoVersao,
        boolean catalogoDegradado,
        Instant publishedAt,
        String etag) {}
