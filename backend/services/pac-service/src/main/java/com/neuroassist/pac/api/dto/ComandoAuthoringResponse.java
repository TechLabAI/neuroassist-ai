package com.neuroassist.pac.api.dto;

import com.neuroassist.pac.domain.model.Comando;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for Comando authoring (create / edit / list / get).
 *
 * <p>Never exposes JPA entities — always mapped from domain {@link Comando} (RULE: entity != DTO).
 *
 * <p>{@code etag} is a quoted integer string (RFC 7232), e.g. {@code "\"1\""}, derived from {@code
 * rowVersion + 1} following the same ETag convention used by PacienteController.
 */
public record ComandoAuthoringResponse(
        UUID id,
        UUID pacienteId,
        String rotulo,
        String necessidade,
        UUID primitivoPrincipalId,
        UUID primitivoSecundarioId,
        String sensibilidade,
        int tempoMinGestoMs,
        int tempoMaxGestoMs,
        String urgencia,
        String estado,
        int calibracaoVersao,
        int rowVersion,
        String etag,
        Instant createdAt,
        Instant updatedAt) {

    /** Factory method — maps from domain model. Never called with a JPA entity directly. */
    public static ComandoAuthoringResponse from(Comando c) {
        return new ComandoAuthoringResponse(
                c.id(),
                c.pacienteId(),
                c.rotulo(),
                c.necessidade(),
                c.primitivoPrincipalId(),
                c.primitivoSecundarioId(),
                c.sensibilidade() != null ? c.sensibilidade().name() : null,
                c.tempoMinGestoMs(),
                c.tempoMaxGestoMs(),
                c.urgencia() != null ? c.urgencia().name() : null,
                c.estado() != null ? c.estado().name() : null,
                c.calibracaoVersao(),
                c.rowVersion(),
                "\"" + (c.rowVersion() + 1) + "\"",
                c.createdAt(),
                c.updatedAt());
    }
}
