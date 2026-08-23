package com.neuroassist.evnt.application.service;

import com.neuroassist.evnt.application.port.in.HistOutboxDrainUseCase;
import com.neuroassist.evnt.application.port.out.HistFactRedeliveryPort;
import com.neuroassist.evnt.application.port.out.HistOutboxPort;
import com.neuroassist.evnt.infrastructure.security.TenantContextHolder;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redelivers audit facts stranded in the outbox (E2E-017).
 *
 * <p>Modelled on pac-service's drain, because the mistake it prevents is one this platform already
 * made: a durable row that nothing reads back is worse than no row at all — its presence makes the
 * queue look healthy while the thing it represents never happens (defect C-44).
 *
 * <p><b>Bounded batch.</b> A backlog accumulated over an outage would otherwise be replayed in one
 * transaction, holding a connection for as long as it takes and hammering a hist-service that has
 * just come back up.
 *
 * <p><b>Stops on the first failure.</b> If hist-service is down the remaining entries fail
 * identically, and burning their retry budget against a known outage is how a transient problem
 * becomes a permanent one.
 */
@Slf4j
@Service
public class HistOutboxDrainService implements HistOutboxDrainUseCase {

    private final HistOutboxPort outboxPort;
    private final HistFactRedeliveryPort redeliveryPort;
    private final int batchSize;
    private final int maxRetries;

    public HistOutboxDrainService(
            HistOutboxPort outboxPort,
            HistFactRedeliveryPort redeliveryPort,
            @Value("${evnt.hist-outbox.batch-size:50}") int batchSize,
            @Value("${evnt.hist-outbox.max-retries:10}") int maxRetries) {
        this.outboxPort = outboxPort;
        this.redeliveryPort = redeliveryPort;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
    }

    @Override
    @Transactional
    public DrainResult drainCurrentTenant() {
        String tenantId = TenantContextHolder.require(); // RULE-07

        List<HistOutboxPort.PendingFact> pending = outboxPort.findPending(batchSize);
        if (pending.isEmpty()) {
            return new DrainResult(0, 0, 0);
        }

        int delivered = 0;
        int givenUp = 0;
        for (HistOutboxPort.PendingFact fact : pending) {
            if (fact.retryCount() >= maxRetries) {
                outboxPort.markFailed(fact.id(), "max retries exceeded");
                givenUp++;
                log.error(
                        "Audit fact {} ({}) given up after {} attempts — tenant={}. It will NOT be"
                                + " delivered; hist-service never accepted it.",
                        fact.factId(),
                        fact.factType(),
                        fact.retryCount(),
                        tenantId);
                continue;
            }
            if (redeliveryPort.redeliver(fact.payloadJson())) {
                outboxPort.markSent(fact.id());
                delivered++;
            } else {
                outboxPort.markRetried(fact.id(), "hist-service unavailable");
                break;
            }
        }

        log.info(
                "Audit outbox drain tenant={} attempted={} delivered={} givenUp={}",
                tenantId,
                pending.size(),
                delivered,
                givenUp);
        return new DrainResult(pending.size(), delivered, givenUp);
    }
}
