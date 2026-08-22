package com.neuroassist.pac.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain model — Paciente (system of record).
 *
 * <p>RULE-01: zero Spring imports. RULE-02: zero JPA imports. Pure POJO record. {@code
 * cuidadorResponsavelIds} are cross-service references to ACS Cuidador — no FK enforcement here.
 * {@code dispositivoEdgeId} is a cross-service reference to EDGE DispositivoEdge.
 *
 * <p>{@code catalogoVersao} is a monotonic counter bumped atomically whenever a published Comando
 * changes (publish, recalibrate, retire). It drives the ETag for {@code GET .../catalogo-ativo} and
 * enables conditional GET with 304 Not Modified (PAC-019 / P6).
 */
public record Paciente(
        UUID id,
        String tenantId,
        String nome,
        UUID dispositivoEdgeId,
        List<UUID> cuidadorResponsavelIds,
        int rowVersion,
        int catalogoVersao,
        Instant createdAt,
        Instant updatedAt,

        /**
         * When the personal data was erased on request (LGPD), or {@code null} while the patient is
         * active.
         *
         * <p>The row and its id survive erasure on purpose: {@code comando} and {@code
         * paciente_cuidador} reference the id, and the point of the exercise is to remove the
         * personal data while keeping the record of what happened — the same tension hist-service
         * resolves with crypto-shredding (ADR-006).
         */
        Instant erasedAt) {

    /** Marker written over {@code nome} once the personal data is erased. */
    public static final String ERASED_NOME = "[dados apagados]";

    public boolean isErased() {
        return erasedAt != null;
    }
}
