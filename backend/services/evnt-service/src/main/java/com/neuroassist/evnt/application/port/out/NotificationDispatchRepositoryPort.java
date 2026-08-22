package com.neuroassist.evnt.application.port.out;

import com.neuroassist.evnt.domain.model.NotificationDispatch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDispatchRepositoryPort {

    NotificationDispatch save(NotificationDispatch dispatch);

    Optional<NotificationDispatch> findById(UUID dispatchId);

    /**
     * Dispatches a caregiver has not acknowledged yet — what dash-service asks for when a panel
     * session opens and needs to show what was missed.
     */
    List<NotificationDispatch> findUnacknowledgedFor(String caregiverId);

    /**
     * Dispatches that never reached a caregiver — {@code QUEUED} (dash-service unreachable) or
     * {@code NO_SESSION} (nobody had a panel open) — oldest attempt first, bounded.
     *
     * <p>Terminal states are excluded: a delivered or acknowledged alarm must never be re-sent.
     */
    List<NotificationDispatch> findUndelivered(int limit);
}
