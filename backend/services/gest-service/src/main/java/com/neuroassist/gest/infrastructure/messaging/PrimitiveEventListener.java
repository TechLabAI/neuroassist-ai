package com.neuroassist.gest.infrastructure.messaging;

import com.neuroassist.gest.domain.event.PrimitiveDeprecated;
import com.neuroassist.gest.domain.event.PrimitiveRegistered;
import com.neuroassist.gest.domain.event.PrimitiveTransitioned;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * In-process listener for TipoGesto domain events (GEST-019). All handlers run {@code AFTER_COMMIT}
 * so that they are not invoked on rollback and any failure inside the listener does not affect the
 * originating HTTP response. Stateless — no injected dependencies; logging only (cache invalidation
 * reserved for P7). Conforms to RULE-05 (no api.* import) and RULE-09 (base package).
 */
@Slf4j
@Component
public class PrimitiveEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPrimitiveRegistered(PrimitiveRegistered event) {
        log.info(
                "[GEST-EVENT] PrimitiveRegistered: id={} nome={} tenant={} actor={}",
                event.tipoGestoId(),
                event.nome(),
                event.tenantRef(),
                event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPrimitiveTransitioned(PrimitiveTransitioned event) {
        log.info(
                "[GEST-EVENT] PrimitiveTransitioned: id={} from={} to={} actor={}",
                event.tipoGestoId(),
                event.fromState(),
                event.toState(),
                event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPrimitiveDeprecated(PrimitiveDeprecated event) {
        log.info(
                "[GEST-EVENT] PrimitiveDeprecated: id={} nome={} actor={}",
                event.tipoGestoId(),
                event.nome(),
                event.actor());
    }
}
