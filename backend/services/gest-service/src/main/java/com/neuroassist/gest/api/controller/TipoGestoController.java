package com.neuroassist.gest.api.controller;

import com.neuroassist.gest.api.dto.TipoGestoRequest;
import com.neuroassist.gest.api.dto.TipoGestoResponse;
import com.neuroassist.gest.api.dto.TransitionRequest;
import com.neuroassist.gest.application.port.in.TipoGestoUseCase;
import com.neuroassist.gest.shared.audit.AuditAction;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<List<TipoGestoResponse>> listAll() {
        return ResponseEntity.ok(tipoGestoUseCase.listAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('gest.tipo-gesto.read')")
    public ResponseEntity<TipoGestoResponse> findById(@PathVariable UUID id) {
        TipoGestoResponse response = tipoGestoUseCase.findById(id);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('gest.tipo-gesto.criar')")
    @AuditAction("CREATE_TIPO_GESTO")
    public ResponseEntity<TipoGestoResponse> register(
            @RequestBody @Valid TipoGestoRequest request) {
        TipoGestoResponse response = tipoGestoUseCase.register(request);
        if (response == null) {
            return ResponseEntity.ok().build();
        }
        URI location = URI.create("/gest-service/tipo-gesto/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize("hasAuthority('gest.tipo-gesto.editar')")
    @AuditAction("UPDATE_TIPO_GESTO")
    public ResponseEntity<TipoGestoResponse> transition(
            @PathVariable UUID id, @RequestBody @Valid TransitionRequest request) {
        return ResponseEntity.ok(tipoGestoUseCase.transition(id, request));
    }
}
