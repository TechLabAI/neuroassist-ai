package com.neuroassist.dash.api.controller;

import com.neuroassist.dash.api.dto.CalibrationProxyRequest;
import com.neuroassist.dash.api.dto.CalibrationProxyResponse;
import com.neuroassist.dash.application.port.in.CalibrationProxyUseCase;
import com.neuroassist.dash.domain.model.CalibrationParams;
import com.neuroassist.dash.domain.model.CalibrationResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Write-through de calibracao para o PAC. Superficie /panel/** (JWT do cuidador).
 */
@Slf4j
@RestController
@RequestMapping("/panel")
@RequiredArgsConstructor
public class CalibrationProxyController {

    private final CalibrationProxyUseCase calibrationProxyUseCase;

    /**
     * POST /panel/calibration
     * Envia calibracao para o PAC e retorna eco do resultado.
     */
    @PostMapping("/calibration")
    @PreAuthorize("hasAuthority('ROLE_CAREGIVER')")
    public ResponseEntity<CalibrationProxyResponse> calibrate(
            @RequestParam String patientId,
            @RequestParam String commandId,
            @RequestBody @Valid CalibrationProxyRequest request,
            Authentication auth) {

        String caregiverId = auth.getName();
        // tenantId extraido do principal via claim "tenant" (preenchido pelo SecurityConfig / P6)
        String tenantId = extractTenantId(auth);

        CalibrationParams params = new CalibrationParams(
                request.sensitivity(),
                request.threshold(),
                request.extra());

        CalibrationResult result = calibrationProxyUseCase.calibrate(
                caregiverId, patientId, commandId, params, tenantId);

        CalibrationProxyResponse response = new CalibrationProxyResponse(
                result.commandId(),
                result.newCalibrationState(),
                result.success());

        return ResponseEntity.ok(response);
    }

    private String extractTenantId(Authentication auth) {
        // Claim "tenant" extraido do JWT pelo SecurityConfig (implementado em P6).
        // Por ora, retorna vazio; substituir quando SecurityConfig estiver completo.
        if (auth.getDetails() instanceof java.util.Map<?, ?> details) {
            Object tenant = details.get("tenant");
            if (tenant instanceof String t) {
                return t;
            }
        }
        return "";
    }
}
