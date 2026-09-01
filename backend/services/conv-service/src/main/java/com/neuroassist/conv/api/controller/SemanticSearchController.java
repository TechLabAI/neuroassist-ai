package com.neuroassist.conv.api.controller;

import com.neuroassist.conv.application.port.in.SemanticSearchUseCase;
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
@RequestMapping("/conv-service/semantic-search")
@RequiredArgsConstructor
public class SemanticSearchController {

    private final SemanticSearchUseCase semanticSearchUseCase;

    @PutMapping("/{id}/searchAssisted")
    @PreAuthorize("hasAuthority('conv.semantic-search.read')")
    public ResponseEntity<Void> searchAssisted() {
        // TODO[SCAFFOLD]: delegate to SemanticSearchUseCase
        throw new UnsupportedOperationException("searchAssisted not implemented");
    }
}
