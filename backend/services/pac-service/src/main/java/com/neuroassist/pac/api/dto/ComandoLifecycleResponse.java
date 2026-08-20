package com.neuroassist.pac.api.dto;

import com.neuroassist.pac.domain.model.Comando;
import com.neuroassist.pac.domain.model.Sensibilidade;
import com.neuroassist.pac.domain.model.Urgencia;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for lifecycle transition endpoints (:validate, :publish, :retire).
 *
 * <p>Includes {@code warnings} for advisory messages (routine publish when GEST unavailable).
 * Warnings are always an empty list for validate and retire responses.
 *
 * <p>RULE-04: api.dto has no infrastructure imports. RULE: never expose JPA @Entity directly.
 */
public record ComandoLifecycleResponse(
        UUID id,
        UUID pacienteId,
        String rotulo,
        String necessidade,
        Sensibilidade sensibilidade,
        Urgencia urgencia,
        String estado,
        int calibracaoVersao,
        int rowVersion,
        Instant updatedAt,
        List<String> warnings) {

    /** Etag value for If-Match headers on subsequent operations. */
    public String etag() {
        return String.valueOf(rowVersion);
    }

    /** Maps a domain Comando to this response DTO with no warnings. */
    public static ComandoLifecycleResponse from(Comando c) {
        return from(c, List.of());
    }

    /** Maps a domain Comando to this response DTO with advisory warnings. */
    public static ComandoLifecycleResponse from(Comando c, List<String> warnings) {
        return new ComandoLifecycleResponse(
                c.id(),
                c.pacienteId(),
                c.rotulo(),
                c.necessidade(),
                c.sensibilidade(),
                c.urgencia(),
                c.estado().name(),
                c.calibracaoVersao(),
                c.rowVersion(),
                c.updatedAt(),
                warnings == null ? List.of() : List.copyOf(warnings));
    }
}
