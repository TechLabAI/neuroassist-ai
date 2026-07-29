package com.neuroassist.pac.api.controller;

import com.neuroassist.pac.application.port.in.CatalogoAtivoUseCase;
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
@RequestMapping("/pac-service/catalogo-ativo")
@RequiredArgsConstructor
public class CatalogoAtivoController {

    private final CatalogoAtivoUseCase catalogoAtivoUseCase;

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pac.catalogo-ativo.read')")
    public ResponseEntity<Void> getCatalogoAtivo() {
        // TODO: delegate to CatalogoAtivoUseCase
        throw new UnsupportedOperationException("getCatalogoAtivo not implemented");
    }
}
