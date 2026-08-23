package com.neuroassist.evnt.infrastructure.clients.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.evnt.application.port.out.AuditTrailPort;
import com.neuroassist.evnt.application.port.out.HistOutboxPort;
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
 * <p><b>Fail-open is not the same as fail-forgetful</b> (E2E-017). Until this issue the failure
 * branch logged {@code "was NOT recorded and is lost — evnt-service has no outbox"} and returned: a
 * fact produced while hist-service was down was gone for good, and these are the facts of an
 * <i>alarm</i> — the record that matters most in a clinical audit. The fact now goes to {@code
 * hist_outbox} and {@code HistOutboxDrainService} redelivers it.
 *
 * <p>The outbox write joins the caller's transaction ({@code EventConfirmationService} and {@code
 * ReportIngressService} are both {@code @Transactional}), so an audit fact exists if and only if
 * the event that produced it exists. The request is serialised and stored whole rather than
 * re-derived later, because the {@code factId} must be reused byte-for-byte for hist-service to
 * treat the redelivery as a replay instead of a second fact (defect C-5).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditTrailFeignAdapter implements AuditTrailPort {

    private final AuditTrailClient client;
    private final HistOutboxPort outboxPort;
    private final ObjectMapper objectMapper;

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
                        fact.occurredAt(),
                        // evnt-service facts are about a patient, so the subject IS the scope.
                        "paciente".equals(fact.subjectType()) ? fact.subjectId() : null);
        try {
            var response = client.recordFact(request);
            log.info(
                    "Audit fact {} ({}) recorded at sequenceNo={}",
                    fact.factId(),
                    fact.factType(),
                    response == null ? null : response.sequenceNo());
        } catch (Exception e) {
            log.warn(
                    "Audit fact {} ({}) not delivered, queued for retry: {}",
                    fact.factId(),
                    fact.factType(),
                    e.getMessage());
            enqueueForRetry(fact, request);
        }
    }

    /**
     * Last line of defence. If queueing itself fails, the fact really is lost — and that must be
     * loud, because it is the state this issue exists to eliminate. Throwing here would roll back
     * the caller's transaction, which is exactly the fail-open contract of AUD-10: an audit write
     * must never undo a caregiver's confirmation.
     */
    private void enqueueForRetry(AuditFact fact, AuditTrailClient.RecordAuditFactRequest request) {
        try {
            outboxPort.enqueue(
                    fact.factId(), fact.factType(), objectMapper.writeValueAsString(request));
        } catch (Exception queueFailure) {
            log.error(
                    "Audit fact {} ({}) could NOT be queued and is lost: {}",
                    fact.factId(),
                    fact.factType(),
                    queueFailure.getMessage(),
                    queueFailure);
        }
    }
}
