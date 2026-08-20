package com.neuroassist.dash.api.controller;

import com.neuroassist.dash.api.dto.NotificationDeliveryRequest;
import com.neuroassist.dash.api.dto.NotificationDeliveryResponse;
import com.neuroassist.dash.application.port.in.NotificationDeliveryUseCase;
import com.neuroassist.dash.domain.model.DeliveryResult;
import com.neuroassist.dash.domain.model.NotificationPayload;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Internal dispatch sink controller (DASH-006). Surface: /internal/** — authenticated by SVC_EVNT
 * authority. Not exposed via API Gateway to the browser (INT-DASH-001).
 */
@Slf4j
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class NotificationDeliveryController {

    private final NotificationDeliveryUseCase notificationDeliveryUseCase;

    /**
     * Receives a notification from EVNT and delivers it to the caregiver's SSE session. Returns 200
     * with delivery status (delivered | no_open_session).
     */
    @PostMapping
    @PreAuthorize("hasAuthority('SVC_EVNT')")
    public ResponseEntity<NotificationDeliveryResponse> deliver(
            @Valid @RequestBody NotificationDeliveryRequest request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader) {

        String tenantId = tenantIdHeader != null ? tenantIdHeader : "unknown";

        NotificationPayload payload =
                new NotificationPayload(
                        request.dispatchId(), request.tier(), request.message(), Instant.now());

        DeliveryResult result =
                notificationDeliveryUseCase.deliver(request.caregiverId(), payload, tenantId);

        return ResponseEntity.ok(
                new NotificationDeliveryResponse(result.dispatchId(), result.status()));
    }
}
