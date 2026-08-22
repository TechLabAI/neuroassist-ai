package com.neuroassist.evnt.infrastructure.clients.adapter;

import com.neuroassist.evnt.application.port.out.AuditTrailPort;
import com.neuroassist.evnt.infrastructure.clients.AuditTrailClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Appends audit facts to hist-service.
 *
 * <p>Replaces {@code AuditTrailNoOpAdapter}, a literal no-op — which is why AUD-06 and AUD-07 were
 * absent features rather than failing integrations.
 *
 * <p><b>Fail-open (AUD-10).</b> An audit write must not fail the business operation that produced
 * it: a caregiver's confirmation is not rolled back because hist-service is down. The failure is
 * logged at {@code warn} and the call returns.
 *
 * <p><b>Known gap, deliberately not hidden.</b> pac-service backs the same concern with a durable
 * outbox, so a fact survives a hist-service outage and is retried. evnt-service has no outbox
 * table, so a fact lost here is lost for good. Adding one is real work (a table, a sweeper, and the
 * {@code factId} minted once and reused across retries, per defect C-5) and belongs to the EVNT
 * backlog, not to this session — but the loss is silent from the caller's point of view, so it is
 * stated here rather than left to be discovered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditTrailFeignAdapter implements AuditTrailPort {

    private final AuditTrailClient client;

    @Override
    public void record(AuditFact fact) {
        var request =
                new AuditTrailClient.RecordAuditFactRequest(
                        fact.factId(),
                        fact.factType(),
                        new AuditTrailClient.SubjectRefDto(fact.subjectType(), fact.subjectId()),
                        new AuditTrailClient.ActorRefDto(
                                fact.actorCaregiverId(), fact.actorDisplayName()),
                        fact.payload(),
                        fact.occurredAt());
        try {
            var response = client.recordFact(request);
            log.info(
                    "Audit fact {} ({}) recorded at sequenceNo={}",
                    fact.factId(),
                    fact.factType(),
                    response == null ? null : response.sequenceNo());
        } catch (Exception e) {
            log.warn(
                    "Audit fact {} ({}) was NOT recorded and is lost — evnt-service has no outbox:"
                            + " {}",
                    fact.factId(),
                    fact.factType(),
                    e.getMessage());
        }
    }
}
