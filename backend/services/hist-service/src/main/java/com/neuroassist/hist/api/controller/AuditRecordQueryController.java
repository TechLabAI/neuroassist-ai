package com.neuroassist.hist.api.controller;

import com.neuroassist.hist.application.port.in.AuditRecordQueryUseCase;
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
@RequestMapping("/hist-service/audit-record-query")
@RequiredArgsConstructor
public class AuditRecordQueryController {

    private final AuditRecordQueryUseCase auditRecordQueryUseCase;

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('hist.audit-record-query.read')")
    public ResponseEntity<Void> findById() {
        // TODO: delegate to AuditRecordQueryUseCase
        throw new UnsupportedOperationException("findById not implemented");
    }

}
