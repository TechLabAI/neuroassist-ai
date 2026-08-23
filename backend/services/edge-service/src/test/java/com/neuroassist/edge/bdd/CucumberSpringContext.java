package com.neuroassist.edge.bdd;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.neuroassist.edge.integration.BaseIntegrationTest;
import io.cucumber.spring.CucumberContextConfiguration;
import java.text.ParseException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Spring context configuration for Cucumber BDD tests. Extends {@link BaseIntegrationTest} to
 * inherit the Testcontainers PostgreSQL singleton setup (D-532). Adds:
 *
 * <ul>
 *   <li>Static WireMock server for GEST detection-profile + ACS service-token stubs.
 *   <li>{@code @DynamicPropertySource} to override {@code neuroassist.gest.url} and {@code
 *       neuroassist.acs.service-token.url} to the WireMock port.
 *   <li>{@code @TestConfiguration} inner class providing a {@code @Primary JwtDecoder} that
 *       accepts unsigned {@code PlainJWT} tokens without signature validation (test seam), and a
 *       {@code cucumber-glue} scoped {@link TestContext} bean for per-scenario shared state.
 * </ul>
 *
 * <p><b>WireMock lifecycle:</b> The server is started once (static initializer) and shared across
 * all scenarios in the test run. {@link BddHooks#cleanUpAndResetStubs()} resets all stubs before
 * each scenario and re-installs the default ACS service-token stub, so scenarios are isolated.
 *
 * <p><b>JwtDecoder:</b> Spring Boot's {@code OAuth2ResourceServerJwtConfiguration} is annotated
 * with {@code @ConditionalOnMissingBean(JwtDecoder.class)}. Because the inner {@code TestConfig}
 * provides a {@code JwtDecoder} bean, the auto-configuration is skipped and the {@code jwk-set-uri}
 * in {@code application-test.yml} is never contacted.
 */
@CucumberContextConfiguration
public class CucumberSpringContext extends BaseIntegrationTest {

    /**
     * Single WireMock server for all BDD scenarios. Listens on a random port; its base URL is
     * injected via {@code @DynamicPropertySource} as both GEST URL and ACS service-token URL so all
     * outbound HTTP from the running application context is intercepted here.
     */
    public static final WireMockServer WIRE_MOCK;

    static {
        WIRE_MOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        WIRE_MOCK.start();
    }

    /**
     * Override GEST and ACS URLs to point at WireMock. Takes priority over {@code
     * application-test.yml} (D-532). Both use the same WireMock port because their paths do not
     * overlap: ACS publishes {@code POST /oauth2/token/service}; GEST publishes {@code GET
     * /gest-service/detection-profile/devices/*}.
     */
    @DynamicPropertySource
    static void configureWireMockUrls(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + WIRE_MOCK.port();
        registry.add("neuroassist.gest.url", () -> url);
        registry.add("neuroassist.acs.service-token.url", () -> url);
    }

    /**
     * Test-only Spring beans for the Cucumber context. Not annotated with {@code @Configuration}
     * to avoid component scan; loaded automatically because it is a static inner class of the
     * {@code @CucumberContextConfiguration} class (Spring Boot Test convention).
     */
    @TestConfiguration
    static class TestConfig {

        /**
         * JWT decoder that accepts unsigned {@link com.nimbusds.jwt.PlainJWT} tokens produced by
         * {@link TestJwtFactory}. Overrides Spring Boot's auto-configured {@code NimbusJwtDecoder}
         * (which would try to fetch the JWKS from the unreachable {@code localhost:9999}).
         *
         * <p>{@code @Primary} ensures this bean is chosen when multiple {@code JwtDecoder} beans
         * exist. In practice, because this class is inside a {@code @TestConfiguration} loaded
         * before {@code OAuth2ResourceServerJwtConfiguration} — which is {@code
         * @ConditionalOnMissingBean(JwtDecoder.class)} — the auto-configured decoder is never
         * created.
         */
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            return token -> {
                try {
                    JWT parsed = JWTParser.parse(token);
                    JWTClaimsSet claimsSet = parsed.getJWTClaimsSet();
                    Map<String, Object> claimsMap = new HashMap<>(claimsSet.getClaims());

                    Instant issuedAt =
                            claimsSet.getIssueTime() != null
                                    ? claimsSet.getIssueTime().toInstant()
                                    : Instant.now().minusSeconds(60);
                    Instant expiresAt =
                            claimsSet.getExpirationTime() != null
                                    ? claimsSet.getExpirationTime().toInstant()
                                    : Instant.now().plusSeconds(3600);

                    return Jwt.withTokenValue(token)
                            .headers(h -> h.put("alg", "none"))
                            .claims(c -> c.putAll(claimsMap))
                            .issuedAt(issuedAt)
                            .expiresAt(expiresAt)
                            .build();
                } catch (ParseException e) {
                    throw new JwtException("Test JWT decoder: cannot parse token — " + e.getMessage());
                }
            };
        }

        /**
         * Per-scenario shared state. The {@code cucumber-glue} scope is registered by the
         * {@code cucumber-spring} module when the Cucumber context is bootstrapped. A fresh
         * {@link TestContext} is created for each scenario and destroyed when the scenario ends.
         */
        @Bean
        @Scope("cucumber-glue")
        TestContext testContext() {
            return new TestContext();
        }
    }
}
