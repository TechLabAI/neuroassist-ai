package com.neuroassist.edge.bdd;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Factory for unsigned test JWTs ({@code alg: none}) used in BDD scenarios. Tokens are created via
 * Nimbus {@link PlainJWT} — the same library Spring Security uses for JWT parsing — and decoded by
 * the {@code @Primary JwtDecoder} bean in {@link CucumberSpringContext.TestConfig}, which accepts
 * them without signature validation.
 *
 * <p>Static constants define the deterministic device/patient UUIDs. All tests use these same IDs
 * so JdbcTemplate DML and assertions are consistent across step definition classes.
 *
 * <p>Tenant mapping: "empresa-test" → schema {@code t_emp_test}. The test JWT carries {@code
 * tenant_id = TEST_TENANT} (a fixed UUID) so that {@code TenantContextFilter} binds it to {@code
 * TenantContextHolder} and Hibernate routes connections to the correct schema. {@code
 * init-schema.sql} inserts a row in {@code neuroassist_global.tenant} mapping {@code TEST_TENANT →
 * schema_nome = "t_emp_test"}, so {@code MultiTenantConnectionProvider.resolveSchema()} exercises
 * the real production catalogue path instead of the identifier-as-schema fallback.
 */
public final class TestJwtFactory {

    /** "d7b1" — primary test device. */
    public static final UUID D7B1_ID = UUID.fromString("d7b10000-0000-0000-0000-000000000001");

    /** "e8c2" — secondary test device (used in identity-mismatch scenario). */
    public static final UUID E8C2_ID = UUID.fromString("e8c20000-0000-0000-0000-000000000001");

    /** "9c2a" — test patient paired to d7b1. */
    public static final UUID PATIENT_9C2A_ID =
            UUID.fromString("9c2a0000-0000-0000-0000-000000000001");

    /** "1f4b" — second test patient for re-pair scenarios. */
    public static final UUID PATIENT_1F4B_ID =
            UUID.fromString("1f4b0000-0000-0000-0000-000000000001");

    /**
     * "z9z9" — deliberately unknown device. Not inserted into the DB so "not registered" steps
     * produce the expected 404 from the service.
     */
    public static final UUID Z9Z9_ID = UUID.fromString("a0b00000-0000-0000-0000-999999999999");

    /**
     * Fixed UUID for the test tenant. Corresponds to the row inserted in {@code
     * neuroassist_global.tenant} by {@code init-schema.sql}, which maps this UUID to {@code
     * schema_nome = "t_emp_test"}. Mirrors production: ACS issues the UUID in the JWT, and {@code
     * MultiTenantConnectionProvider} resolves the schema from the catalogue.
     */
    public static final String TEST_TENANT = "00000000-0000-0000-0000-000000000001";

    private static final long TOKEN_VALIDITY_SECONDS = 3600L;

    private TestJwtFactory() {}

    /**
     * Creates a device JWT. {@code sub} = deviceId string, {@code tenant_id} = tenantId, {@code
     * scope} = "edge.device".
     */
    public static String createDeviceToken(UUID deviceId, String tenantId) {
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .subject(deviceId.toString())
                        .claim("tenant_id", tenantId)
                        .claim("scope", "edge.device")
                        .issueTime(Date.from(Instant.now().minusSeconds(10)))
                        .expirationTime(
                                Date.from(Instant.now().plusSeconds(TOKEN_VALIDITY_SECONDS)))
                        .build();
        return new PlainJWT(claims).serialize();
    }

    /**
     * Creates an operator JWT. {@code sub} = "operator-test", {@code tenant_id} = tenantId, {@code
     * scope} = "edge.read edge.manage".
     */
    public static String createOperatorToken(String tenantId) {
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .subject("operator-test")
                        .claim("tenant_id", tenantId)
                        .claim("scope", "edge.read edge.manage")
                        .issueTime(Date.from(Instant.now().minusSeconds(10)))
                        .expirationTime(
                                Date.from(Instant.now().plusSeconds(TOKEN_VALIDITY_SECONDS)))
                        .build();
        return new PlainJWT(claims).serialize();
    }

    /**
     * Creates a read-only operator JWT. {@code sub} = "operator-readonly", {@code tenant_id} =
     * tenantId, {@code scope} = "edge.read" only — no {@code edge.manage}. Used to test that
     * manage-only endpoints reject callers without the required authority.
     */
    public static String createReadOnlyToken(String tenantId) {
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .subject("operator-readonly")
                        .claim("tenant_id", tenantId)
                        .claim("scope", "edge.read")
                        .issueTime(Date.from(Instant.now().minusSeconds(10)))
                        .expirationTime(
                                Date.from(Instant.now().plusSeconds(TOKEN_VALIDITY_SECONDS)))
                        .build();
        return new PlainJWT(claims).serialize();
    }

    /**
     * Creates a service JWT for GEST-to-EDGE internal calls. {@code sub} = "gest-service", {@code
     * tenant_id} = tenantId, {@code scope} = "edge.internal".
     */
    public static String createServiceToken(String tenantId) {
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .subject("gest-service")
                        .claim("tenant_id", tenantId)
                        .claim("scope", "edge.internal")
                        .issueTime(Date.from(Instant.now().minusSeconds(10)))
                        .expirationTime(
                                Date.from(Instant.now().plusSeconds(TOKEN_VALIDITY_SECONDS)))
                        .build();
        return new PlainJWT(claims).serialize();
    }
}
