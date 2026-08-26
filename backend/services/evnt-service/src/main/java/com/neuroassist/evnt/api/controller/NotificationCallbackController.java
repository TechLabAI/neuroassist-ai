package com.neuroassist.evnt.api.controller;

import com.neuroassist.evnt.application.port.in.NotificationCallbackUseCase;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Delivery-state surface consumed by dash-service.
 *
 * <p>Every path and parameter here mirrors {@code
 * com.neuroassist.dash.infrastructure.clients.EvntClient} verbatim — that client has been calling
 * these six endpoints all along while evnt-service exposed none of them. Designing a "nicer" shape
 * would just move the mismatch.
 *
 * <p>Guarded by {@code SVC_DASH}: dash-service calls with its own service-identity token, and the
 * caregiver travels as an explicit {@code caregiverId} parameter. dash-service already authorised
 * the person at its {@code /panel/**} boundary; the authority of these calls is the service
 * (service-identity.md §4). That also keeps this surface unreachable from the browser — the same
 * posture as the {@code /internal/**} surface of dash-service.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationCallbackController {

    private final NotificationCallbackUseCase notificationCallbackUseCase;

    @PostMapping("/{dispatchId}/delivered")
    @PreAuthorize("hasAuthority('SVC_DASH')")
    public ResponseEntity<Void> delivered(@PathVariable("dispatchId") UUID dispatchId) {
        notificationCallbackUseCase.markDelivered(dispatchId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{dispatchId}/no-session")
    @PreAuthorize("hasAuthority('SVC_DASH')")
    public ResponseEntity<Void> noSession(@PathVariable("dispatchId") UUID dispatchId) {
        notificationCallbackUseCase.markNoSession(dispatchId);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code caregiverId} is optional because dash-service calls this path twice with different
     * arities: from its panel proxy with the caregiver, and from its SSE callback without.
     */
    @PostMapping("/{dispatchId}/ack")
    @PreAuthorize("hasAuthority('SVC_DASH')")
    public ResponseEntity<Void> acknowledge(
            @PathVariable("dispatchId") UUID dispatchId,
            @RequestParam(value = "caregiverId", required = false) String caregiverId) {
        notificationCallbackUseCase.markAcknowledged(dispatchId, caregiverId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unacknowledged")
    @PreAuthorize("hasAuthority('SVC_DASH')")
    public ResponseEntity<List<NotificationCallbackUseCase.PendingNotification>> unacknowledged(
            @RequestParam("caregiverId") String caregiverId) {
        return ResponseEntity.ok(notificationCallbackUseCase.listUnacknowledged(caregiverId));
    }
}
