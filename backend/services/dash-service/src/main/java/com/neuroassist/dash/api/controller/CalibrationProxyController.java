package com.neuroassist.dash.api.controller;

import com.neuroassist.dash.application.port.in.CalibrationProxyUseCase;
import com.neuroassist.dash.shared.audit.AuditAction;
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
@RequestMapping("/dash-service/calibration-proxy")
@RequiredArgsConstructor
public class CalibrationProxyController {

    private final CalibrationProxyUseCase calibrationProxyUseCase;

    @PutMapping("/{id}/updateCalibration")
    @PreAuthorize("hasAuthority('dash.calibration-proxy.editar')")
    @AuditAction("UPDATE_CALIBRATION_PROXY")
    public ResponseEntity<Void> updateCalibration() {
        // TODO: delegate to CalibrationProxyUseCase
        throw new UnsupportedOperationException("updateCalibration not implemented");
    }
}
