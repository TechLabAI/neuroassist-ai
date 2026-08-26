package com.neuroassist.evnt.infrastructure.clients.adapter;

import com.neuroassist.evnt.application.port.out.NotificationDispatchPort;
import com.neuroassist.evnt.domain.model.NotificationDispatch;
import com.neuroassist.evnt.infrastructure.clients.NotificationDispatchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Delivers a notification through dash-service.
 *
 * <p>Replaces {@code NotificationDispatchLogAdapter}, whose entire implementation was {@code
 * log.info("[ALERTA PoC] …")} — no alarm ever left evnt-service.
 *
 * <p><b>Fail-open, unlike the other three adapters.</b> Delivery is durable-retryable state, not a
 * precondition: the Evento is already persisted and the dispatch row already exists, so a
 * dash-service outage must leave the dispatch retryable rather than lose the event. A failure is
 * reported as {@link DispatchOutcome#FAILED} and the caller keeps the dispatch queued (NTF-04).
 *
 * <p>Note that {@code no_open_session} is <b>not</b> a failure — it is dash-service correctly
 * reporting that the caregiver has no session listening right now (NTF-02).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatchFeignAdapter implements NotificationDispatchPort {

    private final NotificationDispatchClient client;

    @Override
    public DispatchOutcome dispatch(NotificationDispatch dispatch, String message) {
        var request =
                new NotificationDispatchClient.NotificationDeliveryRequest(
                        dispatch.caregiverId(), dispatch.id().toString(), dispatch.tier(), message);
        try {
            var response = client.deliver(request);
            String status =
                    response == null || response.status() == null
                            ? DispatchOutcome.FAILED
                            : response.status();
            log.info(
                    "Dispatch {} to caregiver {} → {}",
                    dispatch.id(),
                    dispatch.caregiverId(),
                    status);
            return new DispatchOutcome(status);
        } catch (Exception e) {
            log.warn(
                    "Dispatch {} to caregiver {} failed, staying queued for retry: {}",
                    dispatch.id(),
                    dispatch.caregiverId(),
                    e.getMessage());
            return new DispatchOutcome(DispatchOutcome.FAILED);
        }
    }
}
