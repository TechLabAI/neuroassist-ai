package com.neuroassist.edge.infrastructure.security;

/**
 * Subset of the ACS {@code AutenticacaoResponse} that the EDGE needs from {@code POST
 * /oauth2/token/service}. Unknown properties (refreshToken, sub, papel, authorities...) are ignored
 * by Jackson.
 *
 * <p>Never log an instance of this record — it carries the bearer token.
 *
 * @param accessToken RS256 JWT with {@code token_type=service}
 * @param tokenType always {@code Bearer}
 * @param expiresIn lifetime in seconds
 */
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
