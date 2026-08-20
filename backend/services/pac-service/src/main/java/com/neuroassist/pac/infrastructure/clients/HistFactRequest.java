package com.neuroassist.pac.infrastructure.clients;

import java.time.Instant;
import java.util.UUID;

/**
 * INT-PAC-004 — Request DTO for {@link HistFactClient#recordFact}.
 *
 * <p>Maps from {@link com.neuroassist.pac.domain.model.HistFact} domain record to the hist-service
 * HTTP API body. This is an infrastructure DTO — never exposed in api.* or domain.*.
 *
 * <p>Also used as the JSON payload serialized into {@code hist_outbox.payload_json} for
 * outbox-based retry.
 */
public record HistFactRequest(
        String eventType,
        UUID comandoId,
        UUID pacienteId,
        String empresaId,
        String urgencia,
        int calibracaoVersao,
        String estado,
        Instant occurredAt) {}
