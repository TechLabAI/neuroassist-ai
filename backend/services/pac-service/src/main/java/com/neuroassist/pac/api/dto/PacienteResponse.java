package com.neuroassist.pac.api.dto;

import com.neuroassist.pac.domain.model.Paciente;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for Paciente resources.
 *
 * <p>Never exposes JPA entities (CLAUDE.md: Entidades JPA nunca expostas no controller). The
 * factory method {@link #from(Paciente)} maps from the domain model.
 */
public record PacienteResponse(
        UUID id,
        String nome,
        UUID dispositivoEdgeId,
        List<UUID> cuidadorResponsavelIds,
        int rowVersion,
        Instant createdAt,
        Instant updatedAt) {

    /** ETag value as a quoted string (RFC 7232). E.g. rowVersion=0 → {@code "1"}. */
    public String etag() {
        return "\"" + (rowVersion + 1) + "\"";
    }

    /** Maps a Paciente domain model to the response DTO. */
    public static PacienteResponse from(Paciente p) {
        return new PacienteResponse(
                p.id(),
                p.nome(),
                p.dispositivoEdgeId(),
                p.cuidadorResponsavelIds() != null ? p.cuidadorResponsavelIds() : List.of(),
                p.rowVersion(),
                p.createdAt(),
                p.updatedAt());
    }
}
