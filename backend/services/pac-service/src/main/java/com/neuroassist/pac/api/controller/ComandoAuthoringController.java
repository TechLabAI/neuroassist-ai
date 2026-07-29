package com.neuroassist.pac.api.controller;

import com.neuroassist.pac.application.port.in.ComandoAuthoringUseCase;
import com.neuroassist.pac.shared.audit.AuditAction;
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
@RequestMapping("/pac-service/comando-authoring")
@RequiredArgsConstructor
public class ComandoAuthoringController {

    private final ComandoAuthoringUseCase comandoAuthoringUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('pac.comando-authoring.criar')")
    @AuditAction("CREATE_COMANDO_AUTHORING")
    public ResponseEntity<Void> createDraft() {
        // TODO: delegate to ComandoAuthoringUseCase
        throw new UnsupportedOperationException("createDraft not implemented");
    }

    @PutMapping("/{id}/editDraft")
    @PreAuthorize("hasAuthority('pac.comando-authoring.editar')")
    @AuditAction("UPDATE_COMANDO_AUTHORING")
    public ResponseEntity<Void> editDraft() {
        // TODO: delegate to ComandoAuthoringUseCase
        throw new UnsupportedOperationException("editDraft not implemented");
    }
}
