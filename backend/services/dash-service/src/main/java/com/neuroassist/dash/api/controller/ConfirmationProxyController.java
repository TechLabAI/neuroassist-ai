package com.neuroassist.dash.api.controller;

import com.neuroassist.dash.application.port.in.ConfirmationProxyUseCase;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Write-throughs de confirmacao de evento e ack de notificacao para o EVNT. Superficie /panel/**
 * (JWT do cuidador).
 */
@Slf4j
@RestController
@RequestMapping("/panel")
@RequiredArgsConstructor
public class ConfirmationProxyController {

    private final ConfirmationProxyUseCase confirmationProxyUseCase;

    /** Valores de feedback aceitos para confirmacao de evento. */
    private static final Set<String> VALID_FEEDBACK = Set.of("real", "false", "uncertain");

    /**
     * POST /panel/events/{eventId}/confirm?feedback={feedback} Confirma um evento no EVNT. O
     * feedback deve ser um dos valores aceitos. Retorna 204 No Content; 422 se o feedback for
     * invalido.
     */
    @PostMapping("/events/{eventId}/confirm")
    @PreAuthorize("hasAuthority('ROLE_CAREGIVER')")
    public ResponseEntity<Void> confirmEvent(
            @PathVariable String eventId,
            @RequestParam(required = false, defaultValue = "real") String feedback,
            Authentication auth) {

        if (!VALID_FEEDBACK.contains(feedback)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Invalid feedback value: '" + feedback + "'. Accepted: " + VALID_FEEDBACK);
        }

        String caregiverId = auth.getName();
        String tenantId = extractTenantId(auth);

        confirmationProxyUseCase.confirmEvent(caregiverId, eventId, tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /panel/notifications/{dispatchId}/ack Registra ack de notificacao no EVNT. Retorna 204
     * No Content.
     */
    @PostMapping("/notifications/{dispatchId}/ack")
    @PreAuthorize("hasAuthority('ROLE_CAREGIVER')")
    public ResponseEntity<Void> acknowledgeNotification(
            @PathVariable String dispatchId, Authentication auth) {

        String caregiverId = auth.getName();
        String tenantId = extractTenantId(auth);

        confirmationProxyUseCase.acknowledgeNotification(caregiverId, dispatchId, tenantId);
        return ResponseEntity.noContent().build();
    }

    private String extractTenantId(Authentication auth) {
        // Claim "tenant" extraido do JWT pelo SecurityConfig (implementado em P6).
        if (auth.getDetails() instanceof java.util.Map<?, ?> details) {
            Object tenant = details.get("tenant");
            if (tenant instanceof String t) {
                return t;
            }
        }
        return "";
    }
}
