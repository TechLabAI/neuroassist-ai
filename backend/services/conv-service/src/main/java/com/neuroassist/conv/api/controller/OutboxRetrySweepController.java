package com.neuroassist.conv.api.controller;

import com.neuroassist.conv.application.port.in.OutboxRetrySweepUseCase;
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
@RequestMapping("/conv-service/outbox-retry-sweep")
@RequiredArgsConstructor
public class OutboxRetrySweepController {

    private final OutboxRetrySweepUseCase outboxRetrySweepUseCase;

    @PutMapping("/{id}/sweepPendingFacts")
    @PreAuthorize("hasAuthority('conv.outbox-retry-sweep.reprocessar')")
    public ResponseEntity<Void> sweepPendingFacts() {
        // TODO[SCAFFOLD]: delegate to OutboxRetrySweepUseCase
        throw new UnsupportedOperationException("sweepPendingFacts not implemented");
    }
}
