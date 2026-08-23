package com.neuroassist.pac.application.port.out;

import java.util.UUID;

/**
 * Outbound port — writes the caregiver-patient link into the ACS directory (E2E-015).
 *
 * <p>Distinct from {@link AcsDirectoryPort}, which only <i>resolves</i> a caregiver and travels on
 * the caregiver's own token because that authorization depends on who asked. This one is
 * pac-service acting on its own behalf from a scheduled sweep, where there is no caller to inherit
 * a token from — it uses service identity (see {@code standards/service-identity.md} §5).
 */
public interface AcsLinkPort {

    /**
     * Creates the link. Idempotent on the ACS side: an existing active link answers 200.
     *
     * @return how the drain loop should treat the outcome
     */
    Outcome link(UUID pacienteId, UUID cuidadorId);

    /** Revokes the link. Idempotent on the ACS side: an already-revoked link answers 200. */
    Outcome unlink(UUID pacienteId, UUID cuidadorId);

    /**
     * Three states, deliberately, instead of a boolean.
     *
     * <p>The EDGE paid for the two-state version: a 403 from a downstream service was
     * indistinguishable from the service being down, so an authorization bug looked like an outage
     * and was retried forever. Here the case that matters is the ACS coverage invariant (D-204) —
     * revoking a patient's last active caregiver answers 409, and that will answer 409 every time.
     */
    enum Outcome {
        /** The ACS accepted it (or had already accepted an equivalent request). */
        ACCEPTED,

        /** Transient — the ACS is unreachable or answered 5xx. Try again later. */
        RETRY,

        /**
         * The ACS refused, and will refuse identically on every retry: 409 last-active-link, 422
         * unknown patient, 403. Retrying only burns the budget and hides the refusal.
         */
        REFUSED
    }
}
