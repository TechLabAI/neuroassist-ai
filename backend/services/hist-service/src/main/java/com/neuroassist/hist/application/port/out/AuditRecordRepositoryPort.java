package com.neuroassist.hist.application.port.out;

import com.neuroassist.hist.domain.model.AuditRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port — read/write access to the audit record ledger.
 *
 * <p>Write path: {@link AuditLedgerPort} (write-once insert, ADR-004). Read path: query methods
 * defined here.
 */
public interface AuditRecordRepositoryPort {

    /**
     * Persists a new audit record (write-once semantics). Prefer {@link AuditLedgerPort#insert} for
     * the primary ingestion path.
     */
    AuditRecord save(AuditRecord entity);

    /**
     * Fetches a single record by HIST-owned id within the given tenant. Cross-tenant leakage is
     * prevented by always requiring {@code tenantId}.
     */
    Optional<AuditRecord> findById(UUID id, String tenantId);

    /**
     * Fetches all records for a (streamKey, tenantId) pair ordered by {@code sequenceNo} ascending.
     * Used by {@code ChainVerificationService} to walk the hash chain from genesis (HIST-008).
     */
    List<AuditRecord> findByStreamKeyOrderedBySequenceAsc(String streamKey, String tenantId);

    /**
     * Filter / pagination descriptor for {@link #findFiltered}. All fields except {@code tenantId},
     * {@code page}, and {@code size} are optional (null = no filter).
     */
    record AuditRecordFilter(
            String subjectType,
            UUID subjectId,
            String factType,

            /**
             * Patients the caller may see, or {@code null} for an unrestricted read ({@code
             * hist:read:all}).
             *
             * <p>This replaced {@code actorCaregiverId}, which was applied as {@code caregiver_id =
             * caller} — a predicate on <i>authorship</i>, not on scope. A caregiver could therefore
             * never see a fact produced by a device or by another service about her own patient,
             * which is precisely what the timeline is for (C-16).
             *
             * <p>An <b>empty</b> list is not the same as {@code null}: it means the caller is
             * responsible for no patient and must see nothing.
             */
            List<UUID> allowedPacientes,
            Instant from,
            Instant to,
            String order,
            int page,
            int size) {}

    /** Paginated result from {@link #findFiltered}. */
    record FilteredPage(List<AuditRecord> items, long total) {}

    /**
     * Returns a filtered, paginated slice of the audit ledger scoped to {@code tenantId}. Used by
     * {@code HistoryQueryService} (HIST-008). JPA implementation provided in P3.
     */
    FilteredPage findFiltered(AuditRecordFilter filter, String tenantId);

    /**
     * Finds a record by producer-minted {@code factId} within the given tenant. Used to resolve
     * {@code correctsFactId} references during ingestion.
     */
    Optional<AuditRecord> findByFactId(UUID factId, String tenantId);
}
