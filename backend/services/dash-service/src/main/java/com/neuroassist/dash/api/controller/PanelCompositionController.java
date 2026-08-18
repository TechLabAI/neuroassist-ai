package com.neuroassist.dash.api.controller;

import com.neuroassist.dash.application.port.in.PanelCompositionUseCase;
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
@RequestMapping("/dash-service/panel-composition")
@RequiredArgsConstructor
public class PanelCompositionController {

    private final PanelCompositionUseCase panelCompositionUseCase;

    @GetMapping("/compose-summary")
    @PreAuthorize("hasAuthority('dash.panel-composition.read')")
    public ResponseEntity<Void> composeSummary() {
        // TODO: delegate to PanelCompositionUseCase
        throw new UnsupportedOperationException("composeSummary not implemented");
    }

    @GetMapping("/compose-patient-overview")
    @PreAuthorize("hasAuthority('dash.panel-composition.read')")
    public ResponseEntity<Void> composePatientOverview() {
        // TODO: delegate to PanelCompositionUseCase
        throw new UnsupportedOperationException("composePatientOverview not implemented");
    }

    @GetMapping("/query-history")
    @PreAuthorize("hasAuthority('dash.panel-composition.read')")
    public ResponseEntity<Void> queryHistory() {
        // TODO: delegate to PanelCompositionUseCase
        throw new UnsupportedOperationException("queryHistory not implemented");
    }

    @GetMapping("/query-device-status")
    @PreAuthorize("hasAuthority('dash.panel-composition.read')")
    public ResponseEntity<Void> queryDeviceStatus() {
        // TODO: delegate to PanelCompositionUseCase
        throw new UnsupportedOperationException("queryDeviceStatus not implemented");
    }
}
