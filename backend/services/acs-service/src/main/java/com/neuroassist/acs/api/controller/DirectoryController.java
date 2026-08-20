package com.neuroassist.acs.api.controller;

import com.neuroassist.acs.api.dto.DirectoryResponse;
import com.neuroassist.acs.application.port.in.DirectoryUseCase;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller — DirectoryController. Provides directory context for routing decisions by other
 * services. Used by: evnt-service, dash-service, hist-service for authorization and routing.
 * RULE-04: no infrastructure dependency.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/acs/directory")
@RequiredArgsConstructor
public class DirectoryController {

    private final DirectoryUseCase directoryUseCase;

    /**
     * GET /api/v1/acs/directory?tenantId=&pacienteId= Resolves tenant validity and responsible
     * caregivers for the given patient. Returns DirectoryResponse consumed by
     * evnt-service/dash-service for routing. Requires: acs.directory.read
     */
    @GetMapping
    @PreAuthorize("hasAuthority('acs.directory.read')")
    public ResponseEntity<DirectoryResponse> resolveDirectory(
            @RequestParam UUID tenantId, @RequestParam UUID pacienteId) {
        DirectoryResponse response = directoryUseCase.resolveDirectory(tenantId, pacienteId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/acs/directory/patients/{id}/caregivers — returns active responsible caregivers
     * for a patient. Used by evnt-service and dash-service for routing decisions. Cache-Control:
     * max-age=60 (routing context is relatively stable). Requires: acs.directory.read
     */
    @GetMapping("/patients/{id}/caregivers")
    @PreAuthorize("hasAuthority('acs.directory.read')")
    public ResponseEntity<DirectoryResponse.PatientCaregiversResponse> getPatientCaregivers(
            @PathVariable("id") UUID pacienteId) {
        log.debug("GET /api/v1/acs/directory/patients/{}/caregivers", pacienteId);
        DirectoryResponse.PatientCaregiversResponse response =
                directoryUseCase.getPatientCaregivers(pacienteId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(response);
    }

    /**
     * GET /api/v1/acs/directory/tenants/{id} — returns tenant validity info for cross-service
     * consumption. Returns 404 if tenant not found. Requires: acs.directory.read
     */
    @GetMapping("/tenants/{id}")
    @PreAuthorize("hasAuthority('acs.directory.read')")
    public ResponseEntity<DirectoryResponse.TenantInfoResponse> getTenantInfo(
            @PathVariable("id") UUID tenantId) {
        log.debug("GET /api/v1/acs/directory/tenants/{}", tenantId);
        DirectoryResponse.TenantInfoResponse response = directoryUseCase.getTenantInfo(tenantId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/acs/directory/caregivers/{id}/patient-scope — returns patient UUIDs a caregiver
     * has active links to. Used for data-access scope decisions. Requires: acs.directory.read
     */
    @GetMapping("/caregivers/{id}/patient-scope")
    @PreAuthorize("hasAuthority('acs.directory.read')")
    public ResponseEntity<DirectoryResponse.PatientScopeResponse> getCaregiverPatientScope(
            @PathVariable("id") UUID cuidadorId) {
        log.debug("GET /api/v1/acs/directory/caregivers/{}/patient-scope", cuidadorId);
        DirectoryResponse.PatientScopeResponse response =
                directoryUseCase.getCaregiverPatientScope(cuidadorId);
        return ResponseEntity.ok(response);
    }
}
