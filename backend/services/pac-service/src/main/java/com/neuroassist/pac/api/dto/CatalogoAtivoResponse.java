package com.neuroassist.pac.api.dto;

import com.neuroassist.pac.application.port.in.CatalogoAtivoUseCase.CatalogoAtivoResult;
import com.neuroassist.pac.domain.model.Comando;
import com.neuroassist.pac.domain.model.Sensibilidade;
import com.neuroassist.pac.domain.model.Urgencia;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for {@code GET .../catalogo-ativo/{pacienteId}} (PAC-020).
 *
 * <p>Contains all published Comandos for a patient plus the {@code catalogoVersao} used as the HTTP
 * ETag seed. GEST polls this endpoint with {@code If-None-Match} to detect changes.
 *
 * <p>RULE-04: no infrastructure.* imports. Never exposes JPA @Entity.
 */
public record CatalogoAtivoResponse(
        UUID pacienteId, String empresaId, int catalogoVersao, List<ComandoSummary> comandos) {

    /**
     * Compact projection of a published Comando for the active catalog payload.
     *
     * <p>Only the fields GEST needs for gesture detection are included.
     */
    public record ComandoSummary(
            UUID id,
            String rotulo,
            String necessidade,
            UUID primitivoPrincipalId,
            UUID primitivoSecundarioId,
            Sensibilidade sensibilidade,
            int tempoMinGestoMs,
            int tempoMaxGestoMs,
            Urgencia urgencia,
            int calibracaoVersao,
            Instant updatedAt) {}

    /** ETag value derived from {@code catalogoVersao}. */
    public String etag() {
        return String.valueOf(catalogoVersao);
    }

    /** Maps a {@link CatalogoAtivoResult} to this response DTO. */
    public static CatalogoAtivoResponse from(
            UUID pacienteId, String empresaId, CatalogoAtivoResult result) {
        List<ComandoSummary> summaries =
                result.comandos().stream().map(CatalogoAtivoResponse::toSummary).toList();
        return new CatalogoAtivoResponse(pacienteId, empresaId, result.catalogoVersao(), summaries);
    }

    private static ComandoSummary toSummary(Comando c) {
        return new ComandoSummary(
                c.id(),
                c.rotulo(),
                c.necessidade(),
                c.primitivoPrincipalId(),
                c.primitivoSecundarioId(),
                c.sensibilidade(),
                c.tempoMinGestoMs(),
                c.tempoMaxGestoMs(),
                c.urgencia(),
                c.calibracaoVersao(),
                c.updatedAt());
    }
}
