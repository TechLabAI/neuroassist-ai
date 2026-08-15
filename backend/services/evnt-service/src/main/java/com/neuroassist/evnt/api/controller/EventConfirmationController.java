package com.neuroassist.evnt.api.controller;

import com.neuroassist.evnt.application.port.in.EventConfirmationUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventConfirmationController {

    private final EventConfirmationUseCase eventConfirmationUseCase;

    @PostMapping("/{id}/confirmation")
    public ResponseEntity<Void> confirm(
            @PathVariable UUID id,
            @RequestBody ConfirmationRequest body,
            @RequestHeader(value = "X-Tenant-ID", required = false) String requestTenant) {
        eventConfirmationUseCase.confirm(id, body.verdict(), body.confirmedBy(), requestTenant);
        return ResponseEntity.ok().build();
    }

    public record ConfirmationRequest(String verdict, String confirmedBy) {}
}
