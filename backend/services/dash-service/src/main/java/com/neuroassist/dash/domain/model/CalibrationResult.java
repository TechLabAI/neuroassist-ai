package com.neuroassist.dash.domain.model;

import java.util.List;

/**
 * Resultado da recalibração, devolvido pelo PAC. Zero Spring, zero JPA.
 *
 * <p>Espelha {@code ComandoLifecycleResponse}. O campo {@code newCalibrationState} da forma
 * anterior não existia no produtor — o que o PAC devolve é o {@code estado} do ciclo de vida do
 * comando mais o {@code calibracaoVersao} incrementado.
 *
 * @param warnings avisos consultivos do PAC (ex.: publicação de rotina com GEST indisponível);
 *     repassados ao painel em vez de descartados
 */
public record CalibrationResult(
        String comandoId,
        String sensibilidade,
        String urgencia,
        String estado,
        int calibracaoVersao,
        List<String> warnings) {}
