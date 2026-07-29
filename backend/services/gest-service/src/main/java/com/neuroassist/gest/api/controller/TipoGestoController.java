package com.neuroassist.gest.api.controller;

import com.neuroassist.gest.application.port.in.TipoGestoUseCase;
import com.neuroassist.gest.shared.audit.AuditAction;
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
@RequestMapping("/gest-service/tipo-gesto")
@RequiredArgsConstructor
public class TipoGestoController {

    private final TipoGestoUseCase tipoGestoUseCase;

    @GetMapping
    @PreAuthorize("hasAuthority('gest.tipo-gesto.read')")
    public ResponseEntity<Void> listAll() {
        // TODO: delegate to TipoGestoUseCase
        throw new UnsupportedOperationException("listAll not implemented");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('gest.tipo-gesto.read')")
    public ResponseEntity<Void> findById() {
        // TODO: delegate to TipoGestoUseCase
        throw new UnsupportedOperationException("findById not implemented");
    }

    @PostMapping
    @PreAuthorize("hasAuthority('gest.tipo-gesto.criar')")
    @AuditAction("CREATE_TIPO_GESTO")
    public ResponseEntity<Void> register() {
        // TODO: delegate to TipoGestoUseCase
        throw new UnsupportedOperationException("register not implemented");
    }

    @PutMapping("/{id}/transition")
    @PreAuthorize("hasAuthority('gest.tipo-gesto.editar')")
    @AuditAction("UPDATE_TIPO_GESTO")
    public ResponseEntity<Void> transition() {
        // TODO: delegate to TipoGestoUseCase
        throw new UnsupportedOperationException("transition not implemented");
    }
}
