package com.neuroassist.acs.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO — Authentication token (POST /oauth2/token). LGPD: no credential fields (no senha,
 * credentialHash, privateKey). Includes access token, refresh token, and principal claims.
 */
public record AutenticacaoResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UUID tenant,
        UUID sub,
        String papel,
        List<String> authorities) {}
