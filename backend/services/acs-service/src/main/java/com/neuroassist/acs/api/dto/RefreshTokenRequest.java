package com.neuroassist.acs.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO — Token refresh (POST /oauth2/token/refresh). LGPD: contains only the refresh token,
 * no credential fields.
 */
public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken is required") String refreshToken) {}
