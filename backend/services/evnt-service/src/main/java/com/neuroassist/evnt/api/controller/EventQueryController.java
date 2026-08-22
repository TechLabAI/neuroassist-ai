package com.neuroassist.evnt.api.controller;

import com.neuroassist.evnt.application.port.in.EventQueryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventQueryController {

    private final EventQueryUseCase eventQueryUseCase;

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('evnt.event-query.read')")
    public ResponseEntity<EventQueryUseCase.ActiveEventsResponse> listActive(
            @RequestParam(required = false) String since) {
        // 'since' parameter accepted for polling fallback (EVNT-024); service ignores it for now
        return ResponseEntity.ok(eventQueryUseCase.listActive());
    }
}
