package com.neuroassist.dash.api.controller;

import com.neuroassist.dash.application.port.in.NotificationDeliveryUseCase;
import com.neuroassist.dash.shared.audit.AuditAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// Security: @PreAuthorize derived from rbac.arch.json via the module generator.
// To change which permission gates a method, edit rbac.arch.json and re-run
// be-module-generator.py; do not patch the annotation by hand.
@Slf4j
@RestController
@RequestMapping("/dash-service/notification-delivery")
@RequiredArgsConstructor
public class NotificationDeliveryController {

    private final NotificationDeliveryUseCase notificationDeliveryUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('dash.notification-delivery.enviar')")
    @AuditAction("CREATE_NOTIFICATION_DELIVERY")
    public ResponseEntity<Void> dispatch() {
        // TODO: delegate to NotificationDeliveryUseCase
        throw new UnsupportedOperationException("dispatch not implemented");
    }

    @GetMapping("/poll-unacknowledged")
    @PreAuthorize("hasAuthority('dash.notification-delivery.read')")
    public ResponseEntity<Void> pollUnacknowledged() {
        // TODO: delegate to NotificationDeliveryUseCase
        throw new UnsupportedOperationException("pollUnacknowledged not implemented");
    }

    @GetMapping("/long-poll")
    @PreAuthorize("hasAuthority('dash.notification-delivery.read')")
    public ResponseEntity<Void> longPoll() {
        // TODO: delegate to NotificationDeliveryUseCase
        throw new UnsupportedOperationException("longPoll not implemented");
    }

    @PutMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('dash.notification-delivery.confirmar')")
    @AuditAction("UPDATE_NOTIFICATION_DELIVERY")
    public ResponseEntity<Void> acknowledge() {
        // TODO: delegate to NotificationDeliveryUseCase
        throw new UnsupportedOperationException("acknowledge not implemented");
    }
}
