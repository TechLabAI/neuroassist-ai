package com.neuroassist.evnt.application.port.out;

import com.neuroassist.evnt.domain.model.NotificationDispatch;

/**
 * Hands a notification to dash-service for delivery to a caregiver's open session.
 *
 * <p>The previous signature returned {@code void}, which was accurate for {@code
 * NotificationDispatchLogAdapter} (it logged and returned) but hid the fact that dispatching has an
 * <i>outcome</i>: dash-service answers {@code delivered} or {@code no_open_session}, and those two
 * lead to different dispatch states and different retry behaviour (NTF-01 vs NTF-02).
 */
public interface NotificationDispatchPort {

    /**
     * @param message the text shown to the caregiver — the command label snapshot taken at report
     *     time, so a later recalibration cannot rewrite what the alarm said
     * @return the outcome reported by dash-service
     */
    DispatchOutcome dispatch(NotificationDispatch dispatch, String message);

    /**
     * @param status {@code delivered} when a session took the notification, {@code no_open_session}
     *     when the caregiver had none, {@code failed} when dash-service could not be reached
     */
    record DispatchOutcome(String status) {

        public static final String DELIVERED = "delivered";
        public static final String NO_OPEN_SESSION = "no_open_session";
        public static final String FAILED = "failed";

        public boolean isDelivered() {
            return DELIVERED.equals(status);
        }
    }
}
