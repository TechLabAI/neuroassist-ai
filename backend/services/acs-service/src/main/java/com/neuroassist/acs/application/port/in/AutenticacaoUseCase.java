package com.neuroassist.acs.application.port.in;

import com.neuroassist.acs.api.dto.AutenticacaoRequest;
import com.neuroassist.acs.api.dto.AutenticacaoResponse;
import com.neuroassist.acs.api.dto.RefreshTokenRequest;

/**
 * Inbound port — Authentication use cases. Covers credential-based login (grant_type=password) and
 * token refresh.
 */
public interface AutenticacaoUseCase {

    /**
     * Authenticates a caregiver by login + senha and returns a JWT access token.
     *
     * @param request login credentials (grantType, login, senha)
     * @return AutenticacaoResponse with access token, refresh token and claims
     */
    AutenticacaoResponse authenticate(AutenticacaoRequest request);

    /**
     * Refreshes an access token using a valid refresh token (token rotation).
     *
     * @param request the refresh token request
     * @return new AutenticacaoResponse with rotated tokens
     */
    AutenticacaoResponse refresh(RefreshTokenRequest request);
}
