package com.neuroassist.dash.api.controller;

import com.neuroassist.dash.application.port.in.PanelCompositionUseCase;
import com.neuroassist.dash.domain.model.DeviceStatus;
import com.neuroassist.dash.domain.model.HistoryPage;
import com.neuroassist.dash.domain.model.PanelSummary;
import com.neuroassist.dash.domain.model.PatientOverview;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de composicao de leitura do painel do cuidador.
 *
 * <p>Superficie: {@code /panel/**} — requer JWT do cuidador com authority {@code ROLE_CAREGIVER}.
 * {@code caregiverId} e extraido do claim {@code sub}; {@code tenantId} do claim {@code tenant_id}.
 */
@Slf4j
@RestController
@RequestMapping("/panel")
@RequiredArgsConstructor
public class PanelCompositionController {

    private final PanelCompositionUseCase panelCompositionUseCase;

    /**
     * Home dashboard: resumo de todos os pacientes vinculados ao cuidador.
     *
     * <p>GET /panel/summary
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('dash.panel-composition.read')")
    public ResponseEntity<PanelSummary> getSummary(Authentication auth) {
        String caregiverId = auth.getName();
        String tenantId = extractTenantId(auth);
        log.info("getSummary caregiverId={} tenantId={}", caregiverId, tenantId);
        PanelSummary summary = panelCompositionUseCase.composeSummary(caregiverId, tenantId);
        return ResponseEntity.ok(summary);
    }

    /**
     * Overview detalhado de um paciente.
     *
     * <p>GET /panel/patients/{patientId}/overview
     */
    @GetMapping("/patients/{patientId}/overview")
    @PreAuthorize("hasAuthority('dash.panel-composition.read')")
    public ResponseEntity<PatientOverview> getPatientOverview(
            @PathVariable String patientId, Authentication auth) {
        String caregiverId = auth.getName();
        String tenantId = extractTenantId(auth);
        log.info(
                "getPatientOverview caregiverId={} patientId={} tenantId={}",
                caregiverId,
                patientId,
                tenantId);
        PatientOverview overview =
                panelCompositionUseCase.composePatientOverview(caregiverId, patientId, tenantId);
        return ResponseEntity.ok(overview);
    }

    /**
     * Historico paginado de eventos de um paciente.
     *
     * <p>GET /panel/patients/{patientId}/history?page=0&size=20
     */
    @GetMapping("/patients/{patientId}/history")
    @PreAuthorize("hasAuthority('dash.panel-composition.read')")
    public ResponseEntity<HistoryPage> getHistory(
            @PathVariable String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        String caregiverId = auth.getName();
        String tenantId = extractTenantId(auth);
        log.info(
                "getHistory caregiverId={} patientId={} page={} size={} tenantId={}",
                caregiverId,
                patientId,
                page,
                size,
                tenantId);
        HistoryPage historyPage =
                panelCompositionUseCase.queryHistory(caregiverId, patientId, page, size, tenantId);
        return ResponseEntity.ok(historyPage);
    }

    /**
     * Status de liveness do dispositivo edge de um paciente.
     *
     * <p>GET /panel/patients/{patientId}/device
     */
    @GetMapping("/patients/{patientId}/device")
    @PreAuthorize("hasAuthority('dash.panel-composition.read')")
    public ResponseEntity<DeviceStatus> getDeviceStatus(
            @PathVariable String patientId, Authentication auth) {
        String caregiverId = auth.getName();
        String tenantId = extractTenantId(auth);
        log.info(
                "getDeviceStatus caregiverId={} patientId={} tenantId={}",
                caregiverId,
                patientId,
                tenantId);
        DeviceStatus status =
                panelCompositionUseCase.queryDeviceStatus(caregiverId, patientId, tenantId);
        return ResponseEntity.ok(status);
    }

    // ---- helpers ----

    private String extractTenantId(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getClaimAsString("tenant_id");
        }
        return null;
    }
}
