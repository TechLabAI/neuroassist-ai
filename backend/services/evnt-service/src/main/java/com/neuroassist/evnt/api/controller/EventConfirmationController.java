package com.neuroassist.evnt.api.controller;

import com.neuroassist.evnt.application.port.in.EventConfirmationUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Confirmation of an Evento by a caregiver.
 *
 * <p>Two entry points on purpose, because two callers exist and they are not the same kind of
 * caller:
 *
 * <ul>
 *   <li>{@code POST /{id}/confirmation} — a caregiver's own JWT, carrying {@code
 *       evnt.evento.confirmar}. The body names the confirmer.
 *   <li>{@code POST /{id}/confirm?caregiverId=} — dash-service proxying its panel, with its own
 *       service-identity token ({@code SVC_DASH}). This is the shape {@code EvntClient} has been
 *       calling all along while evnt-service exposed nothing at that path.
 * </ul>
 *
 * <p>Both funnel into the same use case, so the terminal-lock and tenant-isolation invariants of
 * EVNT-022/EVNT-023 cannot be bypassed by picking the other door.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventConfirmationController {

    private final EventConfirmationUseCase eventConfirmationUseCase;

    @PostMapping("/{id}/confirmation")
    @PreAuthorize("hasAuthority('evnt.event-confirmation.confirmar')")
    public ResponseEntity<Void> confirm(
            @PathVariable UUID id,
            @RequestBody ConfirmationRequest body,
            @RequestHeader(value = "X-Tenant-Id", required = false) String requestTenant) {
        eventConfirmationUseCase.confirm(id, body.verdict(), body.confirmedBy(), requestTenant);
        return ResponseEntity.ok().build();
    }

    /**
     * dash-service's write-through. It sends no verdict, because the panel's "confirm" button means
     * the alarm was real — a false positive is reported through its own {@code
     * /panel/events/{id}/confirm?feedback=} surface, which maps onto the endpoint above.
     */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('SVC_DASH')")
    public ResponseEntity<Void> confirmFromPanel(
            @PathVariable UUID id,
            @RequestParam("caregiverId") String caregiverId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String requestTenant) {
        eventConfirmationUseCase.confirm(id, "real", caregiverId, requestTenant);
        return ResponseEntity.ok().build();
    }

    public record ConfirmationRequest(String verdict, String confirmedBy) {}
}
