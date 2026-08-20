package com.neuroassist.pac.api.controller;

import com.neuroassist.pac.api.dto.ComandoLifecycleResponse;
import com.neuroassist.pac.api.dto.RecalibrateRequest;
import com.neuroassist.pac.application.port.in.ComandoLifecycleUseCase;
import com.neuroassist.pac.application.port.in.ComandoLifecycleUseCase.PublishResult;
import com.neuroassist.pac.application.port.in.RecalibrateCommand;
import com.neuroassist.pac.domain.model.Comando;
import com.neuroassist.pac.shared.audit.AuditAction;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PAC-014 / PAC-015 / PAC-016 — ComandoLifecycleController: REST surface for lifecycle transitions.
 *
 * <p>Path: {@code /api/pac/v1/pacientes/{pacienteId}/comandos}. Covers POST /{id}:validate, POST
 * /{id}:publish, POST /{id}:retire.
 *
 * <p>Architectural rules:
 *
 * <ul>
 *   <li>RULE-04: no infrastructure.* imports.
 *   <li>RULE-03: no @Transactional — boundary is in ComandoLifecycleService.
 *   <li>Never returns JPA @Entity — always ComandoLifecycleResponse DTO records.
 * </ul>
 *
 * <p>Emergency gate (SPEC-03) errors surface through GlobalExceptionHandler:
 *
 * <ul>
 *   <li>503 + Retry-After:30 for EligibilityGateUnavailableException
 *   <li>422 for pac.emergency.not-detectable
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/pac/v1/pacientes/{pacienteId}/comandos")
@RequiredArgsConstructor
public class ComandoLifecycleController {

    private final ComandoLifecycleUseCase comandoLifecycleUseCase;

    // -------------------------------------------------------------------------
    // POST /api/pac/v1/pacientes/{pacienteId}/comandos/{id}:validate → 200
    // -------------------------------------------------------------------------

    // Note: pac.comando-lifecycle.read is the permission for validate
    // (permission-catalogue.arch.json
    // does not include a separate 'validar' scope — the read permission guards the validation
    // transition per the generated catalogue).
    @PostMapping("/{id}:validate")
    @PreAuthorize("hasAuthority('pac.comando-lifecycle.read')")
    @AuditAction("VALIDATE_COMANDO")
    public ResponseEntity<ComandoLifecycleResponse> validate(
            @PathVariable UUID pacienteId, @PathVariable UUID id) {

        Comando result = comandoLifecycleUseCase.validate(pacienteId, id);
        ComandoLifecycleResponse response = ComandoLifecycleResponse.from(result);

        log.debug(
                "POST /pacientes/{}/comandos/{}:validate → 200 estado={}",
                pacienteId,
                id,
                result.estado());
        return ResponseEntity.ok().eTag(response.etag()).body(response);
    }

    // -------------------------------------------------------------------------
    // POST /api/pac/v1/pacientes/{pacienteId}/comandos/{id}:publish → 200
    // -------------------------------------------------------------------------

    @PostMapping("/{id}:publish")
    @PreAuthorize("hasAuthority('pac.comando-lifecycle.publicar')")
    @AuditAction("PUBLISH_COMANDO")
    public ResponseEntity<ComandoLifecycleResponse> publish(
            @PathVariable UUID pacienteId, @PathVariable UUID id) {

        PublishResult result = comandoLifecycleUseCase.publish(pacienteId, id);
        ComandoLifecycleResponse response =
                ComandoLifecycleResponse.from(result.comando(), result.warnings());

        log.debug(
                "POST /pacientes/{}/comandos/{}:publish → 200 urgencia={} warnings={}",
                pacienteId,
                id,
                result.comando().urgencia(),
                result.warnings().size());
        return ResponseEntity.ok().eTag(response.etag()).body(response);
    }

    // -------------------------------------------------------------------------
    // POST /api/pac/v1/pacientes/{pacienteId}/comandos/{id}:retire → 200
    // -------------------------------------------------------------------------

    @PostMapping("/{id}:retire")
    @PreAuthorize("hasAuthority('pac.comando-lifecycle.aposentar')")
    @AuditAction("RETIRE_COMANDO")
    public ResponseEntity<ComandoLifecycleResponse> retire(
            @PathVariable UUID pacienteId, @PathVariable UUID id) {

        Comando result = comandoLifecycleUseCase.retire(pacienteId, id);
        ComandoLifecycleResponse response = ComandoLifecycleResponse.from(result);

        log.debug(
                "POST /pacientes/{}/comandos/{}:retire → 200 (soft-delete, row preserved ADR-006)",
                pacienteId,
                id);
        return ResponseEntity.ok().eTag(response.etag()).body(response);
    }

    // -------------------------------------------------------------------------
    // POST /api/pac/v1/pacientes/{pacienteId}/comandos/{id}:recalibrate → 200
    // -------------------------------------------------------------------------

    /**
     * PAC-018 — In-place recalibration of a published Comando.
     *
     * <p>Gate is re-run only on urgency escalation (rotina → emergencia). A lowering of urgency
     * (emergencia → rotina) does NOT re-run the gate.
     *
     * <p>Response includes updated {@code calibracaoVersao} in the ETag header.
     */
    @PostMapping("/{id}:recalibrate")
    @PreAuthorize("hasAuthority('pac.comando-lifecycle.recalibrar')")
    @AuditAction("RECALIBRATE_COMANDO")
    public ResponseEntity<ComandoLifecycleResponse> recalibrate(
            @PathVariable UUID pacienteId,
            @PathVariable UUID id,
            @RequestBody(required = false) RecalibrateRequest body) {

        RecalibrateRequest req =
                body != null ? body : new RecalibrateRequest(null, null, null, null);
        RecalibrateCommand command =
                new RecalibrateCommand(
                        req.urgencia(),
                        req.sensibilidade(),
                        req.tempoMinGestoMs(),
                        req.tempoMaxGestoMs());

        Comando result = comandoLifecycleUseCase.recalibrate(pacienteId, id, command);
        ComandoLifecycleResponse response = ComandoLifecycleResponse.from(result);

        log.debug(
                "POST /pacientes/{}/comandos/{}:recalibrate → 200 calibracaoVersao={}",
                pacienteId,
                id,
                result.calibracaoVersao());
        return ResponseEntity.ok().eTag(response.etag()).body(response);
    }
}
