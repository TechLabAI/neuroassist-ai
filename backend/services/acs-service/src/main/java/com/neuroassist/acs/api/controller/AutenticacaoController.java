package com.neuroassist.acs.api.controller;

import com.neuroassist.acs.api.dto.AutenticacaoRequest;
import com.neuroassist.acs.api.dto.AutenticacaoResponse;
import com.neuroassist.acs.api.dto.RefreshTokenRequest;
import com.neuroassist.acs.application.port.in.AutenticacaoUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints (ACS-006, ACS-007).
 *
 * <p>POST /oauth2/token — credential login (grant_type=password) POST /oauth2/token/refresh — token
 * rotation
 *
 * <p>Both endpoints are PUBLIC (no @PreAuthorize) — authentication happens inside the service.
 * RULE-04: no infrastructure dependency here.
 */
@Slf4j
@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class AutenticacaoController {

    private final AutenticacaoUseCase autenticacaoUseCase;

    /**
     * Issues a JWT access token for valid caregiver credentials. Supports grant_type=password only
     * in this version.
     *
     * @param request login credentials (grantType, login, senha)
     * @return 200 AutenticacaoResponse | 400 validation | 401 invalid-credentials
     */
    @PostMapping("/token")
    public ResponseEntity<AutenticacaoResponse> token(
            @Valid @RequestBody AutenticacaoRequest request) {
        log.debug("POST /oauth2/token grantType={}", request.grantType());
        AutenticacaoResponse response = autenticacaoUseCase.authenticate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Rotates tokens using a valid refresh token.
     *
     * @param request the refresh token
     * @return 200 AutenticacaoResponse with new access + refresh tokens | 401 if invalid
     */
    @PostMapping("/token/refresh")
    public ResponseEntity<AutenticacaoResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        log.debug("POST /oauth2/token/refresh");
        AutenticacaoResponse response = autenticacaoUseCase.refresh(request);
        return ResponseEntity.ok(response);
    }
}
