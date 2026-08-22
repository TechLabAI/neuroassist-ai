package com.neuroassist.evnt.domain.model;

/**
 * The values actually written to {@code notification_dispatch.status}.
 *
 * <p><b>Why this is not the {@link DispatchStatus} enum next door.</b> That enum spells its
 * constants in lower case ({@code queued}, {@code delivered}, …) and is referenced by nothing; the
 * column has always been written with the upper-case strings below, and its Flyway default is
 * {@code 'QUEUED'}. Introducing the enum here would silently change every persisted value. The
 * duplication is recorded as EVNT debt — the same shape as {@code Feedback}, whose {@code
 * nao_avaliado} never matched the {@code NAO_AVALIADO} the code writes.
 *
 * <p>The column is {@code varchar(50)} with no CHECK constraint, so these are conventions enforced
 * by the code alone.
 */
public final class DispatchStatuses {

    /** Persisted and not yet handed to dash-service, or handed over and not delivered. */
    public static final String QUEUED = "QUEUED";

    /** dash-service pushed it to an open caregiver session. */
    public static final String DELIVERED = "DELIVERED";

    /** dash-service had no open session for the caregiver — retryable (NTF-02). */
    public static final String NO_SESSION = "NO_SESSION";

    /** The caregiver acknowledged it. Terminal. */
    public static final String ACKNOWLEDGED = "ACKNOWLEDGED";

    /**
     * Retried to exhaustion and never delivered. Terminal, and deliberately distinct from {@code
     * QUEUED}: an alarm that will never arrive must not be indistinguishable from one still waiting
     * its turn.
     */
    public static final String FAILED = "FAILED";

    private DispatchStatuses() {}
}
