package com.neuroassist.pac.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model — HistFact.
 *
 * <p>RULE-01: zero Spring imports. RULE-02: zero JPA imports. Pure POJO record.
 *
 * <p>Represents one of the three domain facts recorded to HIST (INT-PAC-004):
 *
 * <ul>
 *   <li>{@code comando.published} — emitted when a Comando transitions to published.
 *   <li>{@code comando.recalibrated} — emitted when a published Comando is recalibrated.
 *   <li>{@code comando.retired} — emitted when a Comando transitions to retired.
 * </ul>
 *
 * <p>Instances are constructed via the static factory methods and passed to {@link
 * com.neuroassist.pac.application.port.out.HistFactPort}.
 */
public record HistFact(
        String eventType,
        UUID comandoId,
        UUID pacienteId,
        String tenantId,
        String urgencia,
        int calibracaoVersao,
        String estado,
        Instant occurredAt) {

    /** Factory — {@code comando.published} domain fact. */
    public static HistFact published(Comando c, String tenantId) {
        return new HistFact(
                "comando.published",
                c.id(),
                c.pacienteId(),
                tenantId,
                c.urgencia() != null ? c.urgencia().name() : null,
                c.calibracaoVersao(),
                EstadoComando.published.name(),
                Instant.now());
    }

    /** Factory — {@code comando.recalibrated} domain fact. */
    public static HistFact recalibrated(Comando c, String tenantId) {
        return new HistFact(
                "comando.recalibrated",
                c.id(),
                c.pacienteId(),
                tenantId,
                c.urgencia() != null ? c.urgencia().name() : null,
                c.calibracaoVersao(),
                EstadoComando.published.name(),
                Instant.now());
    }

    /** Factory — {@code comando.retired} domain fact. */
    public static HistFact retired(Comando c, String tenantId) {
        return new HistFact(
                "comando.retired",
                c.id(),
                c.pacienteId(),
                tenantId,
                c.urgencia() != null ? c.urgencia().name() : null,
                c.calibracaoVersao(),
                EstadoComando.retired.name(),
                Instant.now());
    }
}
