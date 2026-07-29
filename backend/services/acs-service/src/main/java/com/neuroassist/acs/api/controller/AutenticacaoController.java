package com.neuroassist.acs.api.controller;

import com.neuroassist.acs.application.port.in.AutenticacaoUseCase;
import com.neuroassist.acs.shared.audit.AuditAction;
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
@RequestMapping("/acs-service/autenticacao")
@RequiredArgsConstructor
public class AutenticacaoController {

    private final AutenticacaoUseCase autenticacaoUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('acs.autenticacao.autenticar')")
    @AuditAction("CREATE_AUTENTICACAO")
    public ResponseEntity<Void> authenticate() {
        // TODO: delegate to AutenticacaoUseCase
        throw new UnsupportedOperationException("authenticate not implemented");
    }
}
