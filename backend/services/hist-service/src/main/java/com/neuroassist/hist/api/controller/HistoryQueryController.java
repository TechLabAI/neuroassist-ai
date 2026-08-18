package com.neuroassist.hist.api.controller;

import com.neuroassist.hist.application.port.in.HistoryQueryUseCase;
import com.neuroassist.hist.shared.audit.AuditAction;
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
@RequestMapping("/hist-service/history-query")
@RequiredArgsConstructor
public class HistoryQueryController {

    private final HistoryQueryUseCase historyQueryUseCase;

    @GetMapping
    @PreAuthorize("hasAuthority('hist.history-query.read')")
    public ResponseEntity<Void> queryTimeline() {
        // TODO: delegate to HistoryQueryUseCase
        throw new UnsupportedOperationException("queryTimeline not implemented");
    }

}
