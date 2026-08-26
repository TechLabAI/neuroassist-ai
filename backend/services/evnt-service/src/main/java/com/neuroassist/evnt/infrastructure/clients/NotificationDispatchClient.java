package com.neuroassist.evnt.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Notification dispatch into dash-service.
 *
 * <p>Was an empty stub with a default url on port 8086 — a port no service in this system listens
 * on. The port it should have served was implemented by {@code NotificationDispatchLogAdapter},
 * which wrote {@code log.info("[ALERTA PoC] …")} and returned. NTF-01..05 were therefore absent
 * features, not failing integrations: no alarm ever left evnt-service.
 *
 * <p>Mirrors {@code com.neuroassist.dash.api.controller.NotificationDeliveryController} verbatim.
 * The endpoint is guarded by {@code SVC_EVNT} — an authority that, until this change, no issuer in
 * the system emitted, exactly like {@code edge.device} before C-18. evnt-service now holds it
 * through its service identity, so the {@code /internal/**} surface of dash-service becomes
 * reachable by evnt-service and by nothing else.
 */
@FeignClient(name = "dash-dispatch-client", url = "${evnt.dash.baseurl:http://dash-service:8090}")
public interface NotificationDispatchClient {

    @PostMapping("/internal/notifications")
    NotificationDeliveryResponse deliver(@RequestBody NotificationDeliveryRequest request);

    /**
     * Mirrors {@code NotificationDeliveryRequest}. All four fields are {@code @NotBlank} on the
     * dash-service side — a null in any of them is a 400, not a silent drop.
     */
    record NotificationDeliveryRequest(
            String caregiverId, String dispatchId, String tier, String message) {}

    /**
     * Mirrors {@code NotificationDeliveryResponse}. {@code status} is {@code delivered} or {@code
     * no_open_session}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record NotificationDeliveryResponse(String dispatchId, String status) {}
}
