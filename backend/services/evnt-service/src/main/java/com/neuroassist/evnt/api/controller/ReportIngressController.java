package com.neuroassist.evnt.api.controller;

import com.neuroassist.evnt.application.port.in.ReportIngressUseCase;
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
@RequestMapping("/evnt-service/report-ingress")
@RequiredArgsConstructor
public class ReportIngressController {

    private final ReportIngressUseCase reportIngressUseCase;

    @PutMapping("/{id}/ingestReport")
    @PreAuthorize("hasAuthority('evnt.report-ingress.ingerir')")
    public ResponseEntity<Void> ingestReport() {
        // TODO: delegate to ReportIngressUseCase
        throw new UnsupportedOperationException("ingestReport not implemented");
    }
}
