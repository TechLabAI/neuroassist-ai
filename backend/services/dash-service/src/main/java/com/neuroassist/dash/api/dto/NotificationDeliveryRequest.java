package com.neuroassist.dash.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /internal/notifications (DASH-006). Sent by EVNT to deliver a notification
 * to a caregiver's SSE session.
 */
public record NotificationDeliveryRequest(
        @NotBlank String caregiverId,
        @NotBlank String dispatchId,
        @NotBlank String tier,
        @NotBlank String message) {}
