package com.neuroassist.acs.api.controller;

import com.neuroassist.acs.api.dto.CaregiverRequest;
import com.neuroassist.acs.api.dto.CaregiverResponse;
import com.neuroassist.acs.api.dto.CaregiverStatusUpdateRequest;
import com.neuroassist.acs.application.port.in.CaregiverUseCase;
import com.neuroassist.acs.domain.model.CuidadorStatus;
import com.neuroassist.acs.shared.audit.AuditAction;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller — CaregiverController. Endpoints for caregiver (cuidador) administration.
 * RULE-04: no infrastructure dependency. Security: COORDENADOR_CLINICA has acs.cuidador.create,
 * acs.cuidador.read, acs.cuidador.deactivate.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/acs/caregivers")
@RequiredArgsConstructor
public class CaregiverController {

    private final CaregiverUseCase caregiverUseCase;

    /**
     * POST /api/v1/acs/caregivers Creates a new caregiver in the given tenant. BCrypt-hashes the
     * senha before persisting (delegated to CaregiverService). Requires: acs.cuidador.create
     */
    @PostMapping
    @PreAuthorize("hasAuthority('acs.cuidador.criar')")
    @AuditAction("CREATE_CAREGIVER")
    public ResponseEntity<CaregiverResponse> create(
            @Valid @RequestBody CaregiverRequest request, @RequestParam UUID tenantId) {
        CaregiverResponse response = caregiverUseCase.create(request, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** GET /api/v1/acs/caregivers/{id} Returns caregiver by ID. Requires: acs.cuidador.read */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('acs.cuidador.read')")
    public ResponseEntity<CaregiverResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(caregiverUseCase.findById(id));
    }

    /**
     * GET /api/v1/acs/caregivers?tenantId= Lists all caregivers for the given tenant. Requires:
     * acs.cuidador.read
     */
    @GetMapping
    @PreAuthorize("hasAuthority('acs.cuidador.read')")
    public ResponseEntity<List<CaregiverResponse>> listByTenant(@RequestParam UUID tenantId) {
        return ResponseEntity.ok(caregiverUseCase.listByTenant(tenantId));
    }

    /**
     * PATCH /api/v1/acs/caregivers/{id}/status Updates caregiver status (ACTIVE ↔ INACTIVE).
     * Requires: acs.cuidador.deactivate
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('acs.cuidador.desativar')")
    @AuditAction("UPDATE_CAREGIVER_STATUS")
    public ResponseEntity<CaregiverResponse> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody CaregiverStatusUpdateRequest request) {
        CaregiverResponse response =
                caregiverUseCase.updateStatus(id, CuidadorStatus.valueOf(request.status()));
        return ResponseEntity.ok(response);
    }
}
