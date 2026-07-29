package com.neuroassist.pac.api.controller;

import com.neuroassist.pac.application.port.in.PacienteUseCase;
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
@RequestMapping("/pac-service/paciente")
@RequiredArgsConstructor
public class PacienteController {

    private final PacienteUseCase pacienteUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('pac.paciente.criar')")
    @AuditAction("CREATE_PACIENTE")
    public ResponseEntity<Void> create() {
        // TODO: delegate to PacienteUseCase
        throw new UnsupportedOperationException("create not implemented");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pac.paciente.read')")
    public ResponseEntity<Void> findById() {
        // TODO: delegate to PacienteUseCase
        throw new UnsupportedOperationException("findById not implemented");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('pac.paciente.read')")
    public ResponseEntity<Void> listAll() {
        // TODO: delegate to PacienteUseCase
        throw new UnsupportedOperationException("listAll not implemented");
    }

    @PutMapping("/{id}/update")
    @PreAuthorize("hasAuthority('pac.paciente.editar')")
    @AuditAction("UPDATE_PACIENTE")
    public ResponseEntity<Void> update() {
        // TODO: delegate to PacienteUseCase
        throw new UnsupportedOperationException("update not implemented");
    }
}
