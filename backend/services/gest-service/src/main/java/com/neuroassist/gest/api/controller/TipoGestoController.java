package com.neuroassist.gest.api.controller;

import com.neuroassist.gest.api.dto.TipoGestoRequest;
import com.neuroassist.gest.api.dto.TipoGestoResponse;
import com.neuroassist.gest.api.dto.TipoGestoTransitionRequest;
import com.neuroassist.gest.application.port.in.TipoGestoUseCase;
import com.neuroassist.gest.domain.model.TipoGesto;
import com.neuroassist.gest.shared.audit.AuditAction;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/gest-service/tipo-gesto")
@RequiredArgsConstructor
public class TipoGestoController {

    private final TipoGestoUseCase tipoGestoUseCase;

    @GetMapping
    @PreAuthorize("hasAuthority('gest.tipo-gesto.read')")
    public ResponseEntity<List<TipoGestoResponse>> listAll() {
        List<TipoGestoResponse> result = tipoGestoUseCase.listAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('gest.tipo-gesto.read')")
    public ResponseEntity<TipoGestoResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(toResponse(tipoGestoUseCase.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('gest.tipo-gesto.criar')")
    @AuditAction("CREATE_TIPO_GESTO")
    public ResponseEntity<TipoGestoResponse> register(@RequestBody TipoGestoRequest request) {
        TipoGesto created = tipoGestoUseCase.register(request.nome());
        return ResponseEntity.status(201).body(toResponse(created));
    }

    @PutMapping("/{id}/transition")
    @PreAuthorize("hasAuthority('gest.tipo-gesto.editar')")
    @AuditAction("UPDATE_TIPO_GESTO")
    public ResponseEntity<TipoGestoResponse> transition(
            @PathVariable String id,
            @RequestBody TipoGestoTransitionRequest request) {
        TipoGesto updated = tipoGestoUseCase.transition(
                id, request.toState(), request.engineFeatureRef(), request.reason());
        return ResponseEntity.ok(toResponse(updated));
    }

    private TipoGestoResponse toResponse(TipoGesto tg) {
        return new TipoGestoResponse(
                tg.id(), tg.nome(), tg.detectabilityState(),
                tg.engineFeatureRef(), tg.createdAt(), tg.updatedAt());
    }
}
