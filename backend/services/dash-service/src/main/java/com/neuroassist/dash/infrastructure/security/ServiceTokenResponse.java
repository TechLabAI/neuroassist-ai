package com.neuroassist.dash.infrastructure.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subset of the ACS {@code AutenticacaoResponse} that a service-identity consumer needs.
 *
 * <p>The full response also carries {@code refreshToken} (always null for service tokens), {@code
 * tenant}, {@code sub}, {@code papel} and {@code authorities}; they are ignored here.
 *
 * @param accessToken the RS256 JWT with {@code token_type=service}
 * @param tokenType always {@code Bearer}
 * @param expiresIn lifetime in seconds
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceTokenResponse(String accessToken, String tokenType, long expiresIn) {}
