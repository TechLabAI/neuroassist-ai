package com.neuroassist.gest.api.dto;

import com.neuroassist.gest.domain.model.DetectionThresholdTable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Perfil de detecção como o agente de borda o recebe.
 *
 * <p>{@code primitives} é novo, e não era um esquecimento cosmético: os primitivos eram
 * persistidos e nunca devolvidos, e o agente sempre-ligado abortava na partida com "Perfil ativo
 * não possui primitivos" contra qualquer GEST real (E2E-033).
 *
 * <p>{@code thresholds} é o que este perfil manda detectar e com que sensibilidade — antes o
 * agente não tinha de onde puxar limiar nenhum e os mantinha como constante de código, igual para
 * todo paciente.
 */
public record DetectionProfileResponse(
        UUID id,
        long version,
        boolean active,
        String contractVersion,
        DetectionThresholdTable thresholds,
        List<DetectionProfilePrimitiveResponse> primitives,
        Instant publishedAt,
        String etag) {}
