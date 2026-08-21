package com.neuroassist.dash.api.controller;

import com.neuroassist.dash.application.port.in.PanelStreamUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE panel stream controller (DASH-004/DASH-005). Surface: /panel/** — authenticated by caregiver
 * JWT. Security fully configured in P6 (DASH-021); @PreAuthorize annotations prepared here.
 */
@Slf4j
@RestController
@RequestMapping("/panel")
@RequiredArgsConstructor
public class PanelStreamController {

    private final PanelStreamUseCase panelStreamUseCase;

    /**
     * Opens an SSE stream for the authenticated caregiver. GET is mandatory for SSE: browsers
     * cannot POST with EventSource API.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('ROLE_CAREGIVER')")
    public ResponseEntity<SseEmitter> stream(Authentication auth) {
        String caregiverId = auth.getName();
        String tenantId = extractTenantId(auth);
        log.info("SSE stream requested by caregiver={} tenant={}", caregiverId, tenantId);
        SseEmitter emitter = panelStreamUseCase.openSession(caregiverId, tenantId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .body(emitter);
    }

    /** Sends a heartbeat to keep the SSE connection alive. */
    @PostMapping("/stream/heartbeat")
    @PreAuthorize("hasAuthority('ROLE_CAREGIVER')")
    public ResponseEntity<Void> heartbeat(Authentication auth) {
        String caregiverId = auth.getName();
        panelStreamUseCase.heartbeat(caregiverId);
        return ResponseEntity.noContent().build();
    }

    /** Closes the active SSE session for the caregiver. */
    @DeleteMapping("/stream")
    @PreAuthorize("hasAuthority('ROLE_CAREGIVER')")
    public ResponseEntity<Void> closeSession(Authentication auth) {
        String caregiverId = auth.getName();
        panelStreamUseCase.closeSession(caregiverId);
        return ResponseEntity.noContent().build();
    }

    /** Reconnects and replays unacknowledged notifications (catch-up). */
    @GetMapping(value = "/stream/catchup", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('ROLE_CAREGIVER')")
    public ResponseEntity<SseEmitter> replayCatchup(Authentication auth) {
        String caregiverId = auth.getName();
        String tenantId = extractTenantId(auth);
        SseEmitter emitter = panelStreamUseCase.replayCatchup(caregiverId, tenantId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .body(emitter);
    }

    private String extractTenantId(Authentication auth) {
        if (auth.getCredentials() instanceof Jwt jwt) {
            Object claim = jwt.getClaim("tenant_id");
            if (claim != null) {
                return claim.toString();
            }
        }
        return "unknown";
    }
}
