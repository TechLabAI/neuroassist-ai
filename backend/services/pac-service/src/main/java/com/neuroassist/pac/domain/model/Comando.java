package com.neuroassist.pac.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model — Comando (catalog item).
 *
 * <p>RULE-01: zero Spring imports. RULE-02: zero JPA imports. Pure POJO record.
 *
 * <p>{@code primitivoPrincipalId} and {@code primitivoSecundarioId} are cross-service references to
 * GEST TipoGesto — no FK enforcement here. Integrity is validated synchronously via port at write
 * time (P4+).
 *
 * <p>State machine: {@code drafted → validated → published → retired}. Retired is terminal and
 * history-preserving (ADR-006). In-place recalibration bumps {@code calibracaoVersao} (ADR-004).
 */
public record Comando(
        UUID id,
        UUID pacienteId,
        String rotulo,
        String necessidade,
        UUID primitivoPrincipalId,
        UUID primitivoSecundarioId,
        Sensibilidade sensibilidade,
        int tempoMinGestoMs,
        int tempoMaxGestoMs,
        Urgencia urgencia,
        EstadoComando estado,
        int calibracaoVersao,
        int rowVersion,
        Instant createdAt,
        Instant updatedAt) {}
