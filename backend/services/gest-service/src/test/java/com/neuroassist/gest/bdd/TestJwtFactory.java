package com.neuroassist.gest.bdd;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;

/**
 * Stateless JWT builder for BDD tests. Uses the RSA keypair from {@link CucumberSpringContext} to
 * sign tokens that the BDD JwtDecoder (backed by the same RSA public key) will accept.
 *
 * <p>Usage: {@code TestJwtFactory.buildManageToken("emp_test")} returns a Bearer token string
 * suitable for the {@code Authorization} header in MockMvc requests.
 */
public final class TestJwtFactory {

    private TestJwtFactory() {}

    /**
     * Builds a signed JWT with the given tenant and scopes.
     *
     * @param tenant tenantId claim value (e.g. "emp_test")
     * @param scopes space-separated authority strings
     * @return serialized JWT string (without "Bearer " prefix)
     */
    public static String buildToken(String tenant, String... scopes) {
        try {
            RSAPrivateKey privateKey =
                    (RSAPrivateKey) CucumberSpringContext.RSA_KEY_PAIR.getPrivate();
            JWSSigner signer = new RSASSASigner(privateKey);

            JWTClaimsSet claims =
                    new JWTClaimsSet.Builder()
                            .subject("bdd-user")
                            .issueTime(new Date())
                            .expirationTime(new Date(System.currentTimeMillis() + 3_600_000L))
                            .claim("tenant", tenant)
                            .claim("scope", String.join(" ", scopes))
                            .build();

            SignedJWT jwt =
                    new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build test JWT for tenant=" + tenant, e);
        }
    }

    /** Builds a full-privilege management token (criar + editar + publicar + read scopes). */
    public static String buildManageToken(String tenant) {
        return buildToken(
                tenant,
                "gest.tipo-gesto.read",
                "gest.tipo-gesto.criar",
                "gest.tipo-gesto.editar",
                "gest.detection-profile.publicar",
                "gest.detection-profile.read",
                "gest.eligibility.read",
                "gest.detection-contract.read",
                "gest.detection-contract.validar");
    }

    /**
     * Builds a read-only token (read scopes only — no criar/editar/publicar). CUIDADORA mirrors:
     * both read and validar for detection-contract (POST is guarded by validar, not read —
     * E2E-026).
     */
    public static String buildReadToken(String tenant) {
        return buildToken(
                tenant,
                "gest.tipo-gesto.read",
                "gest.eligibility.read",
                "gest.detection-profile.read",
                "gest.detection-contract.read",
                "gest.detection-contract.validar");
    }

    /**
     * Builds a token with only tipo-gesto.read scope (used for authorization failure scenarios).
     */
    public static String buildReadOnlyToken(String tenant) {
        return buildToken(tenant, "gest.tipo-gesto.read");
    }
}
