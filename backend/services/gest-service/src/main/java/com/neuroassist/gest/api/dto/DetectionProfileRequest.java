package com.neuroassist.gest.api.dto;

import com.neuroassist.gest.domain.model.DetectionThresholdTable;
import com.neuroassist.gest.domain.model.Sensibilidade;
import java.util.Map;
import java.util.UUID;

/**
 * Payload de {@code POST /detection-profiles} — publica uma nova versão do perfil de detecção.
 *
 * <p>Substitui o antigo {@code Map<String,Object> classificationParams}, que aceitava qualquer
 * coisa. Os dois campos são opcionais: publicar com o corpo vazio adota os limiares padrão e a
 * faixa padrão para todo gesto — que é, valor por valor, o comportamento histórico.
 *
 * @param thresholds tabela das três faixas; {@code null} adota {@link
 *     DetectionThresholdTable#defaults()}
 * @param sensibilidadePorGesto faixa aplicada a cada {@code tipoGestoId}; gesto ausente recebe
 *     {@link Sensibilidade#padrao()}
 */
public record DetectionProfileRequest(
        DetectionThresholdTable thresholds, Map<UUID, Sensibilidade> sensibilidadePorGesto) {}
