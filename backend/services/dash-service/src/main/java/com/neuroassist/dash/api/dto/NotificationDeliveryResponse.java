package com.neuroassist.dash.api.dto;

/**
 * Response body for POST /internal/notifications (DASH-006). Returns the dispatchId and delivery
 * status.
 */
public record NotificationDeliveryResponse(String dispatchId, String status) {}
