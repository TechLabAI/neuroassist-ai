package com.neuroassist.gest.bdd;

import com.neuroassist.gest.application.port.out.EdgePairingPort;
import com.neuroassist.gest.integration.BaseIntegrationTest;
import io.cucumber.spring.CucumberContextConfiguration;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Bootstraps the Spring context for Cucumber step definitions. Extends {@link BaseIntegrationTest}
 * for the same singleton Testcontainers PostgreSQL wiring the other integration tests use.
 *
 * <p>P8 additions:
 *
 * <ul>
 *   <li>{@code @AutoConfigureMockMvc} — enables MockMvc injection in step definition classes.
 *   <li>{@code RSA_KEY_PAIR} — static keypair shared with {@link TestJwtFactory} for signing test
 *       JWTs without an external Authorization Server.
 *   <li>{@code BddJwtDecoderConfig} — replaces the JWK-URI JwtDecoder with one backed by the
 *       embedded RSA public key, matching the tokens signed by TestJwtFactory.
 *   <li>{@code edgePairingPort} — @MockBean that replaces EdgePairingPortStub; Mockito behaviour is
 *       configured per-scenario in DetectionProfileSteps and reset by SharedSteps @Before.
 * </ul>
 */
@CucumberContextConfiguration
@AutoConfigureMockMvc
public class CucumberSpringContext extends BaseIntegrationTest {

    /** RSA-2048 keypair generated once per JVM — used by {@link TestJwtFactory} to sign JWTs. */
    public static final KeyPair RSA_KEY_PAIR;

    static {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            RSA_KEY_PAIR = gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** Replaces EdgePairingPortStub with a Mockito mock for BDD scenario control. */
    @MockBean public EdgePairingPort edgePairingPort;

    /** Provides a @Primary JwtDecoder backed by the BDD RSA public key. */
    @TestConfiguration
    static class BddJwtDecoderConfig {

        @Bean
        @Primary
        JwtDecoder bddTestJwtDecoder() {
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) RSA_KEY_PAIR.getPublic()).build();
        }
    }
}
