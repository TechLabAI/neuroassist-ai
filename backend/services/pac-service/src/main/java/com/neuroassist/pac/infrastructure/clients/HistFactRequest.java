package com.neuroassist.pac.infrastructure.clients;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * INT-PAC-004 — Request DTO for {@link HistFactClient#recordFact}.
 *
 * <p>Mirrors hist-service's {@code RecordAuditFactRequest} ({@code POST /api/v1/hist/audit-facts})
 * field for field. Maps from {@link com.neuroassist.pac.domain.model.HistFact}. This is an
 * infrastructure DTO — never exposed in api.* or domain.*.
 *
 * <p>Also used as the JSON payload serialized into {@code hist_outbox.payload_json} for
 * outbox-based retry — {@code factId} is minted once and reused on every retry so hist-service
 * deduplicates the fact instead of appending it twice (HIST ADR-005).
 */
public record HistFactRequest(
        UUID factId,
        String factType,
        SubjectRef subject,
        ActorRef actor,
        Map<String, Object> payload,
        Instant occurredAt,
        UUID correctsFactId,
        String empresaId,

        /**
         * Patient this fact concerns, for caregiver scoping in hist-service (C-16).
         *
         * <p>Required here, unlike for producers whose subject already is a patient: a command
         * fact's subject is the <i>command</i>, so hist-service cannot derive the patient — and the
         * {@code pacienteId} carried in the payload is encrypted at rest, hence unreadable to a
         * WHERE clause. Omitting it makes the fact visible only with {@code hist:read:all}, which
         * is exactly the hole the caregiver timeline had (defect C-16).
         */
        UUID scopePacienteId) {

    /** The entity the fact is about — {@code paciente | comando | cuidador | dispositivo}. */
    public record SubjectRef(String type, UUID id) {}

    /** Snapshot of the acting caregiver at the time the fact was produced. */
    public record ActorRef(UUID caregiverId, String displayNameSnapshot) {}
}
