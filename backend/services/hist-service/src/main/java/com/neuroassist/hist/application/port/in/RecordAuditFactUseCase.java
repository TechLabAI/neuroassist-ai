package com.neuroassist.hist.application.port.in;

import com.neuroassist.hist.domain.model.AuditRecord;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound port — write-side ingestion of a single audit fact.
 *
 * <p>Idempotent on {@code factId}: a duplicate submission returns the original record with {@code
 * AppendResult.created = false} (ADR-005). Write-once: no update or delete (ADR-004).
 */
public interface RecordAuditFactUseCase {

    /** Subject reference in the command (type string validated against SubjectType enum). */
    record SubjectCommand(String type, UUID id) {}

    /** Actor snapshot in the command. */
    record ActorCommand(UUID caregiverId, String displayNameSnapshot) {}

    /**
     * Command carrying all producer-supplied fields for one audit fact.
     *
     * @param factId producer-minted idempotency key
     * @param factType dot-notation code from the controlled vocabulary (validated against FactType
     *     enum)
     * @param subject subject reference (opaque to HIST)
     * @param actor caregiver snapshot at ingestion time
     * @param payload structured JSON-compatible map; must not contain biometric keys
     * @param occurredAt producer clock; must not be more than 5 min in the future
     * @param correctsFactId optional ref to a prior factId this record corrects
     * @param tenantId derived from the JWT {@code tenant} claim by the calling layer
     */
    record Command(
            UUID factId,
            String factType,
            SubjectCommand subject,
            ActorCommand actor,
            Map<String, Object> payload,
            Instant occurredAt,
            UUID correctsFactId,
            String tenantId,

            /**
             * Patient this fact concerns, for caregiver scoping (C-16). Optional on the wire: when
             * the subject already is a patient, hist-service derives it from the subject.
             */
            UUID scopePacienteId) {}

    /**
     * Result returned to the controller.
     *
     * @param record the persisted (or previously-persisted) AuditRecord
     * @param created {@code true} if a new record was appended; {@code false} if this was an
     *     idempotent replay
     */
    record AppendResult(AuditRecord record, boolean created) {}

    /**
     * Appends (or replays) an audit fact.
     *
     * @param command the ingestion command
     * @return the persisted record and whether it was newly created
     * @throws com.neuroassist.hist.domain.model.UnknownFactTypeException if factType is not in v1
     *     vocabulary
     * @throws com.neuroassist.hist.domain.model.ForbiddenPayloadException if payload contains
     *     biometric keys
     */
    AppendResult append(Command command);
}
