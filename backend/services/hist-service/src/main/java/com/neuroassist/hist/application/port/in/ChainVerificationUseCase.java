package com.neuroassist.hist.application.port.in;

import com.neuroassist.hist.domain.model.ChainVerificationResult;
import java.time.Instant;
import java.util.UUID;

/**
 * Inbound port — on-demand hash-chain verification for one (tenant, subject) stream. Exposed at
 * {@code GET /api/v1/hist/history/verify}. Requires {@code hist:read} scope (coordinators / DPO use
 * case).
 */
public interface ChainVerificationUseCase {

    /**
     * Command carrying the stream to verify and an optional time window.
     *
     * @param subjectType subject type string (e.g. {@code "paciente"})
     * @param subjectId subject UUID
     * @param from optional lower bound on {@code recordedAt}; verification walks from genesis
     *     anyway
     * @param to optional upper bound on {@code recordedAt}
     * @param tenantId caller's tenant (from JWT {@code tenant} claim)
     */
    record VerificationCommand(
            String subjectType, UUID subjectId, Instant from, Instant to, String tenantId) {}

    /**
     * Recomputes the hash chain for the given stream and reports integrity. Reads-only; never
     * mutates any record.
     *
     * @param command the verification parameters
     * @return the verification result with status {@code intact}, {@code broken}, or {@code empty}
     */
    ChainVerificationResult verifyStream(VerificationCommand command);
}
