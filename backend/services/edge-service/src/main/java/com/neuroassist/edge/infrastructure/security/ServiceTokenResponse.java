package com.neuroassist.edge.infrastructure.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subset of the ACS {@code AutenticacaoResponse} that the EDGE needs from {@code POST
 * /oauth2/token/service}. Unknown properties (refreshToken, sub, papel, authorities...) are ignored
 * by Jackson.
 *
 * <p>{@code ignoreUnknown = true} is stated explicitly rather than left to the Jackson default, so
 * that ACS may return additional fields without breaking this consumer no matter how the
 * service-wide deserialization config is set later.
 *
 * <p>Never log an instance of this record — it carries the bearer token.
 *
 * @param accessToken RS256 JWT with {@code token_type=service}
 * @param tokenType always {@code Bearer}
 * @param expiresIn lifetime in seconds
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceTokenResponse(String accessToken, String tokenType, long expiresIn) {

    /** Redacted — prevents the token leaking through a log statement or an exception message. */
    @Override
    public String toString() {
        return "ServiceTokenResponse[accessToken=***, tokenType="
                + tokenType
                + ", expiresIn="
                + expiresIn
                + "]";
    }
}
