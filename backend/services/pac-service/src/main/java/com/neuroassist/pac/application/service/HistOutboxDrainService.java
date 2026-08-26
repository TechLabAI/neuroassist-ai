package com.neuroassist.pac.application.service;

import com.neuroassist.pac.application.port.in.HistOutboxDrainUseCase;
import com.neuroassist.pac.application.port.out.HistFactRedeliveryPort;
import com.neuroassist.pac.application.port.out.HistOutboxPort;
import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the hist-service outbox (defect C-44).
 *
 * <p>The outbox existed and was written to correctly; nothing ever read it back. A fact produced
 * while hist-service was down was durable and permanently undelivered — which is the worst of both
 * worlds, because the row's presence made the queue look healthy.
 *
 * <p><b>Batch, not drain-everything.</b> A backlog accumulated over an outage would otherwise be
 * replayed in one transaction, holding a connection for as long as it takes and hammering a
 * hist-service that has just come back up. Each tick moves a bounded number of entries.
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
            @Value("${neuroassist.hist-outbox.batch-size:50}") int batchSize,
            @Value("${neuroassist.hist-outbox.max-retries:10}") int maxRetries) {
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
                outboxPort.markFailed(fact.id());
                givenUp++;
                log.error(
                        "Audit fact {} ({}) given up after {} attempts — tenant={}. It will NOT be"
                                + " delivered; hist-service never accepted it.",
                        fact.id(),
                        fact.eventType(),
                        fact.retryCount(),
                        tenantId);
                continue;
            }
            if (redeliveryPort.redeliver(fact.payloadJson())) {
                outboxPort.markSent(fact.id());
                delivered++;
            } else {
                outboxPort.markRetried(fact.id());
                // Stop the batch on the first failure: if hist-service is down, the remaining
                // entries will fail identically, and burning their retry budget against a known
                // outage is how a transient problem becomes a permanent one.
                break;
            }
        }

        log.info(
                "Outbox drain tenant={} attempted={} delivered={} givenUp={}",
                tenantId,
                pending.size(),
                delivered,
                givenUp);
        return new DrainResult(pending.size(), delivered, givenUp);
    }
}
