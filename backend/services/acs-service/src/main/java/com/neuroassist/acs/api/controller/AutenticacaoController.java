package com.neuroassist.acs.api.controller;

import com.neuroassist.acs.api.dto.AutenticacaoRequest;
import com.neuroassist.acs.api.dto.AutenticacaoResponse;
import com.neuroassist.acs.api.dto.DeviceTokenRequest;
import com.neuroassist.acs.api.dto.RefreshTokenRequest;
import com.neuroassist.acs.api.dto.ServiceTokenRequest;
import com.neuroassist.acs.application.port.in.AutenticacaoUseCase;
import com.neuroassist.acs.application.service.DeviceTokenService;
import com.neuroassist.acs.application.service.ServiceTokenService;
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
    private final ServiceTokenService serviceTokenService;
    private final DeviceTokenService deviceTokenService;

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
     * Issues a service-identity token for service-to-service calls (ACS-030).
     *
     * <p>PUBLIC endpoint, like {@code /oauth2/token} — authentication happens inside the service,
     * against the BCrypt hash configured in {@code acs.service-clients}. Every rejection reason
     * returns the same 401.
     *
     * @param request clientId + clientSecret + tenantId the token is scoped to
     * @return 200 AutenticacaoResponse (no refreshToken) | 400 validation | 401 invalid-credentials
     */
    @PostMapping("/token/service")
    public ResponseEntity<AutenticacaoResponse> serviceToken(
            @Valid @RequestBody ServiceTokenRequest request) {
        log.debug(
                "POST /oauth2/token/service clientId={} tenantId={}",
                request.clientId(),
                request.tenantId());
        return ResponseEntity.ok(serviceTokenService.issueToken(request));
    }

    /**
     * Issues a device-identity token for an edge device (E2E-005, {@code
     * standards/service-identity.md} §7).
     *
     * <p>PUBLIC endpoint: the device authenticates by the body itself, exactly like {@code
     * /oauth2/token}. acs-service does not check the secret — it asks edge-service, which owns the
     * credential — and then signs a token whose tenant is the one edge-service reported.
     *
     * <p>Every rejection (unknown client, wrong secret, retired device, edge-service unavailable)
     * is the same 401, and the {@code clientSecret} is never logged.
     *
     * @param request clientId + clientSecret handed to the device at enroll
     * @return 200 AutenticacaoResponse (no refreshToken) | 400 validation | 401 invalid-credentials
     */
    @PostMapping("/token/device")
    public ResponseEntity<AutenticacaoResponse> deviceToken(
            @Valid @RequestBody DeviceTokenRequest request) {
        log.debug("POST /oauth2/token/device clientId={}", request.clientId());
        return ResponseEntity.ok(deviceTokenService.issueToken(request));
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
