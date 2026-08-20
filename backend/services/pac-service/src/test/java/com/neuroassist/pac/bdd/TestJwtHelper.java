package com.neuroassist.pac.bdd;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Test utility for creating RSA-signed JWTs compatible with Spring Security OAuth2 resource server.
 *
 * <p>P9 (PAC-025): generates signed JWTs for BDD integration tests. The RSA key pair is generated
 * once at class load time. The public key is exposed as JWKS JSON to the WireMock JWK server so
 * Spring Security can verify tokens. {@link
 * com.neuroassist.pac.integration.ExternalServicesWireMock} stubs the JWK endpoint.
 *
 * <p>Scopes follow the PAC authority convention (dot-separated):
 *
 * <ul>
 *   <li>{@code pac.paciente.criar} — POST /pacientes
 *   <li>{@code pac.paciente.read} — GET /pacientes
 *   <li>{@code pac.paciente.editar} — PUT /pacientes/{id}
 *   <li>{@code pac.comando-authoring.criar}
 *   <li>{@code pac.comando-authoring.editar}
 *   <li>{@code pac.comando-lifecycle.read}
 *   <li>{@code pac.comando-lifecycle.publicar}
 *   <li>{@code pac.comando-lifecycle.aposentar}
 *   <li>{@code pac.comando-lifecycle.recalibrar}
 *   <li>{@code pac.catalogo-ativo.read}
 * </ul>
 */
public final class TestJwtHelper {

    /** All PAC scopes — used for "superuser" test tokens. */
    public static final String[] ALL_SCOPES = {
        "pac.paciente.criar",
        "pac.paciente.read",
        "pac.paciente.editar",
        "pac.comando-authoring.criar",
        "pac.comando-authoring.editar",
        "pac.comando-lifecycle.read",
        "pac.comando-lifecycle.publicar",
        "pac.comando-lifecycle.aposentar",
        "pac.comando-lifecycle.recalibrar",
        "pac.catalogo-ativo.read"
    };

    /** Scope for catalog read — feature file uses "pac:catalog:read" alias. */
    public static final String[] CATALOG_READ_SCOPES = {"pac.catalogo-ativo.read"};

    /**
     * A scope that does NOT give catalog access — used to trigger 403 in the BDD scenario that
     * verifies insufficient-scope rejection. Feature file refers to this as "pac:command:read".
     */
    public static final String[] COMMAND_READ_ONLY_SCOPES = {"pac.paciente.read"};

    private static final RSAKey RSA_KEY;
    private static final JWSSigner SIGNER;
    private static final String KEY_ID = "pac-test-key-1";
    private static final String ISSUER = "http://test-issuer";

    static {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            RSA_KEY =
                    new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                            .privateKey((RSAPrivateKey) pair.getPrivate())
                            .keyID(KEY_ID)
                            .algorithm(JWSAlgorithm.RS256)
                            .build();
            SIGNER = new RSASSASigner(RSA_KEY);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(
                    "Failed to generate RSA key pair for BDD tests: " + e.getMessage());
        }
    }

    private TestJwtHelper() {}

    /**
     * Returns the JWKS JSON representation of the public key — served by the JWK WireMock so Spring
     * Security can validate tokens signed by this helper.
     */
    public static String getJwksJson() {
        JWKSet jwkSet = new JWKSet(RSA_KEY.toPublicJWK());
        return jwkSet.toString();
    }

    /**
     * Creates a signed JWT with the given {@code empresaId} claim and all PAC scopes.
     *
     * @param empresaId value of the {@code empresaId} JWT claim
     * @return serialized signed JWT
     */
    public static String createToken(String empresaId) {
        return createToken(empresaId, ALL_SCOPES);
    }

    /**
     * Creates a signed JWT with the given {@code empresaId} claim and the specified scopes.
     *
     * @param empresaId value of the {@code empresaId} JWT claim
     * @param scopes authority values to include as the {@code scope} claim (space-separated)
     * @return serialized signed JWT
     */
    public static String createToken(String empresaId, String... scopes) {
        try {
            List<String> scopeList = Arrays.asList(scopes);
            String scopeString = String.join(" ", scopeList);

            JWTClaimsSet claims =
                    new JWTClaimsSet.Builder()
                            .issuer(ISSUER)
                            .subject("test-user")
                            .claim("empresaId", empresaId)
                            .claim("scope", scopeString)
                            .expirationTime(new Date(System.currentTimeMillis() + 3_600_000L))
                            .issueTime(new Date())
                            .build();

            SignedJWT jwt =
                    new SignedJWT(
                            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(),
                            claims);
            jwt.sign(SIGNER);
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test JWT: " + e.getMessage(), e);
        }
    }
}
