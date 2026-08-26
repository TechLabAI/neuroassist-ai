package com.neuroassist.pac.application.port.in;

/**
 * Inbound port — redelivers audit facts that were persisted while hist-service was unreachable.
 *
 * <p>Driven by a scheduler, which is why it takes no arguments: the tenant comes from {@code
 * TenantContextHolder}, bound by the caller before the transaction opens.
 */
public interface HistOutboxDrainUseCase {

    /** Drains one batch for the tenant currently bound to the thread. */
    DrainResult drainCurrentTenant();

    record DrainResult(int attempted, int delivered, int givenUp) {

        public boolean idle() {
            return attempted == 0;
        }
    }
}
