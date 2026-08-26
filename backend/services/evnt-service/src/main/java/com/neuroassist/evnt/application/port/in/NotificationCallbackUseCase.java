package com.neuroassist.evnt.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * Delivery-state callbacks reported by dash-service, plus the backlog query it makes when a panel
 * session opens.
 *
 * <p>This use case exists because dash-service has been calling six endpoints on evnt-service that
 * evnt-service never exposed — {@code EvntClient} was written from what DASH assumed EVNT would
 * offer, and its own tests stubbed that assumption. It is the same defect family catalogued in
 * plan-e2e-integration.md §11, with the direction reversed: here dash-service is the consumer that
 * was tested against its own mock.
 *
 * <p>The signatures below mirror {@code com.neuroassist.dash.infrastructure.clients.EvntClient}
 * verbatim rather than being designed afresh, so the existing consumer keeps working.
 */
public interface NotificationCallbackUseCase {

    /** dash-service pushed the notification to an open caregiver session (NTF-01). */
    void markDelivered(UUID dispatchId);

    /** dash-service had no open session for the caregiver — stays retryable (NTF-02). */
    void markNoSession(UUID dispatchId);

    /**
     * The caregiver acknowledged the notification. Terminal, and idempotent: acknowledging twice is
     * not an error, because dash-service reports the ack both from its panel proxy and from its SSE
     * callback.
     */
    void markAcknowledged(UUID dispatchId, String caregiverId);

    /** Notifications this caregiver has not acknowledged yet. */
    List<PendingNotification> listUnacknowledged(String caregiverId);

    /**
     * @param message the command label snapshot taken when the report arrived — what the alarm
     *     said, not what the command says now
     */
    record PendingNotification(String dispatchId, String tier, String message, String issuedAt) {}
}
