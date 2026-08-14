package com.neuroassist.gest.infrastructure.messaging;

import com.neuroassist.gest.domain.event.DetectionProfilePublished;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * In-process listener for the DetectionProfilePublished domain event (GEST-020). Runs {@code
 * AFTER_COMMIT} so it is not invoked on rollback and any failure inside the listener does not
 * affect the originating HTTP response. Stateless — logging only (cache invalidation reserved for
 * P7). Conforms to RULE-05 (no api.* import) and RULE-09 (base package).
 */
@Slf4j
@Component
public class DetectionProfilePublishedListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDetectionProfilePublished(DetectionProfilePublished event) {
        log.info(
                "[GEST-EVENT] DetectionProfilePublished: profileId={} version={} at={}",
                event.profileId(),
                event.version(),
                event.at());
    }
}
