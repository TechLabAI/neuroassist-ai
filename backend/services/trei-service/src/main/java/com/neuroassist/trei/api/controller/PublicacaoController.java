package com.neuroassist.trei.api.controller;

import com.neuroassist.trei.application.port.in.PublicacaoUseCase;
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
@RequestMapping("/trei-service/publicacao")
@RequiredArgsConstructor
public class PublicacaoController {

    private final PublicacaoUseCase publicacaoUseCase;

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('trei.publicacao.read')")
    public ResponseEntity<Void> getPublicacaoStatus() {
        // TODO[SCAFFOLD]: delegate to PublicacaoUseCase
        throw new UnsupportedOperationException("getPublicacaoStatus not implemented");
    }
}
