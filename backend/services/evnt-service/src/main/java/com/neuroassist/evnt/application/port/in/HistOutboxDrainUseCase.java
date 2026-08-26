package com.neuroassist.evnt.application.port.in;

/**
 * Inbound port — drains the audit outbox for the tenant bound to the current thread (E2E-017).
 *
 * <p>The tenant is an input even though it appears in no parameter: {@code hist_outbox} lives in a
 * per-tenant schema and the caller is a scheduler with no request to inherit one from.
 */
public interface HistOutboxDrainUseCase {

    DrainResult drainCurrentTenant();

    /**
     * @param attempted entries read this tick
     * @param delivered entries hist-service accepted
     * @param givenUp entries moved to {@code failed}
     */
    record DrainResult(int attempted, int delivered, int givenUp) {}
}
