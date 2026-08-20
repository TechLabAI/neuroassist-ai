package com.neuroassist.hist.bdd;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import org.springframework.stereotype.Component;

/**
 * Factory for BDD test JWT tokens. Creates HMAC-256 signed tokens using the same secret key as
 * {@link BddJwtConfig#bddJwtDecoder()}. Scope claims are space-separated strings interpreted by
 * Spring Security's {@code JwtGrantedAuthoritiesConverter} as {@code SCOPE_<scope>} authorities.
 *
 * <p>Scoped to the BDD test Spring context — never used in production.
 */
@Component
public class BddJwtTokenFactory {

    private final MACSigner signer;

    public BddJwtTokenFactory() {
        try {
            this.signer = new MACSigner(BddJwtConfig.TEST_SECRET_BYTES);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to create BDD JWT signer", e);
        }
    }

    /**
     * Creates a signed JWT token for the given tenant and scopes.
     *
     * @param tenantId the tenant identifier (set in {@code tenant_id} and {@code tenant} claims)
     * @param scopes   space-separated OAuth2 scopes (e.g. "hist:write")
     * @return serialized signed JWT string
     */
    public String createToken(String tenantId, String scopes) {
        return createTokenWithSubject(tenantId, "test-user", scopes);
    }

    /**
     * Creates a signed JWT token with an explicit subject (used for caregiver-scope tests where
     * the {@code sub} claim identifies the caregiver).
     */
    public String createTokenWithSubject(String tenantId, String subject, String scopes) {
        try {
            JWTClaimsSet claims =
                    new JWTClaimsSet.Builder()
                            .subject(subject)
                            .issuer("http://bdd-test-issuer")
                            .claim("tenant_id", tenantId)
                            .claim("tenant", tenantId)
                            .claim("scope", scopes)
                            .expirationTime(new Date(System.currentTimeMillis() + 3_600_000L))
                            .issueTime(new Date())
                            .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign BDD JWT token", e);
        }
    }

    /**
     * Creates a token with no scopes (used to test 403 scenarios where scope is missing or empty).
     */
    public String createNoScopeToken(String tenantId) {
        try {
            JWTClaimsSet claims =
                    new JWTClaimsSet.Builder()
                            .subject("test-user-no-scope")
                            .issuer("http://bdd-test-issuer")
                            .claim("tenant_id", tenantId)
                            .claim("tenant", tenantId)
                            .expirationTime(new Date(System.currentTimeMillis() + 3_600_000L))
                            .issueTime(new Date())
                            .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign no-scope BDD JWT token", e);
        }
    }
}
