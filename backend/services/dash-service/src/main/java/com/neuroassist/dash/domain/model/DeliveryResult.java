package com.neuroassist.dash.domain.model;

/** Result of a notification delivery attempt via SSE. Zero JPA/Spring — pure domain record. */
public record DeliveryResult(String dispatchId, String status) {

    /** Notification was successfully sent to the caregiver's SSE session. */
    public static DeliveryResult delivered(String dispatchId) {
        return new DeliveryResult(dispatchId, "delivered");
    }

    /** Caregiver has no active SSE session; EVNT should retry later. */
    public static DeliveryResult noSession(String dispatchId) {
        return new DeliveryResult(dispatchId, "no_open_session");
    }

    /** Notification was already delivered (idempotent duplicate). */
    public static DeliveryResult alreadyDelivered(String dispatchId) {
        return new DeliveryResult(dispatchId, "delivered");
    }
}
