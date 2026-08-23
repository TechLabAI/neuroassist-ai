package com.neuroassist.pac.application.port.in;

/**
 * Inbound port — drains the directory outbox for the tenant currently bound to the thread
 * (E2E-015).
 *
 * <p>The tenant is an input to this use case even though it appears in no parameter: every table it
 * touches lives in a per-tenant schema, and the caller is a scheduler with no request to inherit
 * one from. See {@code DirectoryOutboxScheduler} for why the binding happens there and not here.
 */
public interface DirectoryOutboxDrainUseCase {

    DrainResult drainCurrentTenant();

    /**
     * @param attempted entries read in this tick
     * @param delivered entries the ACS accepted
     * @param givenUp entries moved to {@code failed} — retries exhausted or refused terminally
     */
    record DrainResult(int attempted, int delivered, int givenUp) {}
}
