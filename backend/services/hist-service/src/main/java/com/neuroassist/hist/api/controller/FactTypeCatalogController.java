package com.neuroassist.hist.api.controller;

import com.neuroassist.hist.api.dto.FactTypeCatalogResponse;
import com.neuroassist.hist.application.port.in.FactTypeCatalogUseCase;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller — fact-type controlled vocabulary.
 *
 * <p>Surface: {@code GET /api/v1/hist/fact-types} Returns the active v1 vocabulary list. Requires
 * {@code hist:read} scope.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hist/fact-types")
@RequiredArgsConstructor
public class FactTypeCatalogController {

    private final FactTypeCatalogUseCase factTypeCatalogUseCase;

    /** Returns the list of all active fact-type codes in the current vocabulary version. */
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_hist:read')")
    public ResponseEntity<List<FactTypeCatalogResponse>> listActive() {
        List<FactTypeCatalogResponse> entries =
                factTypeCatalogUseCase.listActive().stream()
                        .map(e -> new FactTypeCatalogResponse(e.code(), e.version()))
                        .toList();
        return ResponseEntity.ok(entries);
    }
}
