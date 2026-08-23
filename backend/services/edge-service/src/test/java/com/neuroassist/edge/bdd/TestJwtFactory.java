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
 * tenant_id = "t_emp_test"} so that {@code TenantContextFilter} binds it to {@code
 * TenantContextHolder} and Hibernate routes connections to the correct schema. When {@code
 * MultiTenantConnectionProvider} cannot find the tenant in {@code neuroassist_global.tenant} (the
 * global catalogue does not exist in tests), it falls back to using the identifier itself as the
 * schema name — which is exactly {@code t_emp_test}.
 */
public final class TestJwtFactory {

    /** "d7b1" — primary test device. */
    public static final UUID D7B1_ID =
            UUID.fromString("d7b10000-0000-0000-0000-000000000001");

    /** "e8c2" — secondary test device (used in identity-mismatch scenario). */
    public static final UUID E8C2_ID =
            UUID.fromString("e8c20000-0000-0000-0000-000000000001");

    /** "9c2a" — test patient paired to d7b1. */
    public static final UUID PATIENT_9C2A_ID =
            UUID.fromString("9c2a0000-0000-0000-0000-000000000001");

    /**
     * "z9z9" — deliberately unknown device. Not inserted into the DB so "not registered" steps
     * produce the expected 404 from the service.
     */
    public static final UUID Z9Z9_ID =
            UUID.fromString("a0b00000-0000-0000-0000-999999999999");

    /** Schema name used by the test tenant. */
    public static final String TEST_TENANT = "t_emp_test";

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
