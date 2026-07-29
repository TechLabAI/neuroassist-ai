package com.neuroassist.evnt.api.controller;

import com.neuroassist.evnt.application.port.in.EventQueryUseCase;
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
@RequestMapping("/evnt-service/event-query")
@RequiredArgsConstructor
public class EventQueryController {

    private final EventQueryUseCase eventQueryUseCase;

    @GetMapping
    @PreAuthorize("hasAuthority('evnt.event-query.read')")
    public ResponseEntity<Void> findById() {
        // TODO: delegate to EventQueryUseCase
        throw new UnsupportedOperationException("findById not implemented");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('evnt.event-query.read')")
    public ResponseEntity<Void> listActive() {
        // TODO: delegate to EventQueryUseCase
        throw new UnsupportedOperationException("listActive not implemented");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('evnt.event-query.read')")
    public ResponseEntity<Void> listAll() {
        // TODO: delegate to EventQueryUseCase
        throw new UnsupportedOperationException("listAll not implemented");
    }
}
