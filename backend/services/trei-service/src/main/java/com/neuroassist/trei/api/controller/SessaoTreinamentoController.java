package com.neuroassist.trei.api.controller;

import com.neuroassist.trei.application.port.in.SessaoTreinamentoUseCase;
import com.neuroassist.trei.shared.audit.AuditAction;
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
@RequestMapping("/trei-service/sessao-treinamento")
@RequiredArgsConstructor
public class SessaoTreinamentoController {

    private final SessaoTreinamentoUseCase sessaoTreinamentoUseCase;

    @PostMapping("/start-session")
    @PreAuthorize("hasAuthority('trei.sessao-treinamento.iniciar')")
    @AuditAction("CREATE_SESSAO_TREINAMENTO")
    public ResponseEntity<Void> startSession() {
        // TODO[SCAFFOLD]: delegate to SessaoTreinamentoUseCase
        throw new UnsupportedOperationException("startSession not implemented");
    }

    @PostMapping("/request-proposal")
    @PreAuthorize("hasAuthority('trei.sessao-treinamento.criar')")
    @AuditAction("CREATE_SESSAO_TREINAMENTO")
    public ResponseEntity<Void> requestProposal() {
        // TODO[SCAFFOLD]: delegate to SessaoTreinamentoUseCase
        throw new UnsupportedOperationException("requestProposal not implemented");
    }
}
