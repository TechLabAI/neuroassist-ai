package com.neuroassist.dash.application.port.in;

import com.neuroassist.dash.domain.model.DeliveryResult;
import com.neuroassist.dash.domain.model.NotificationPayload;

/**
 * Inbound port — delivers notifications from EVNT to the caregiver's SSE session
 * (DASH-005/DASH-006). Called by the internal dispatch endpoint (POST /internal/notifications).
 */
public interface NotificationDeliveryUseCase {

    /**
     * Delivers a notification payload to the caregiver's active SSE session. Idempotent: duplicate
     * dispatchIds are ignored (returns "delivered" without re-sending).
     */
    DeliveryResult deliver(String caregiverId, NotificationPayload payload, String tenantId);
}
