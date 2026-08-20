package com.neuroassist.dash.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.neuroassist.dash.infrastructure.sse.PanelSessionRegistry;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base para testes de integracao do dash-service. Zero datasource (ADR-004: BFF zero-state).
 * Nao usa Testcontainers PostgreSQL nem Artemis — servico e totalmente stateless no MVP.
 *
 * <p>Inicializa 5 WireMock servers (um por upstream: ACS, PAC, EVNT, HIST, EDGE) em portas
 * dinamicas. As URLs sao injetadas via @DynamicPropertySource antes do Spring context ser criado.
 * Um par RSA de teste e gerado uma vez para assinar JWTs nos testes.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    // ---- WireMock servers — um por upstream ----
    public static final WireMockServer acsWireMock;
    public static final WireMockServer pacWireMock;
    public static final WireMockServer evntWireMock;
    public static final WireMockServer histWireMock;
    public static final WireMockServer edgeWireMock;

    // ---- RSA key pair para JWT de teste ----
    public static final RSAPrivateKey TEST_RSA_PRIVATE_KEY;
    public static final RSAPublicKey TEST_RSA_PUBLIC_KEY;

    static {
        // Gera par RSA 2048 bits (usado em toda a suite; gerado apenas uma vez)
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            TEST_RSA_PRIVATE_KEY = (RSAPrivateKey) kp.getPrivate();
            TEST_RSA_PUBLIC_KEY = (RSAPublicKey) kp.getPublic();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }

        // Inicia WireMock servers em portas dinamicas
        acsWireMock = new WireMockServer(wireMockConfig().dynamicPort());
        pacWireMock = new WireMockServer(wireMockConfig().dynamicPort());
        evntWireMock = new WireMockServer(wireMockConfig().dynamicPort());
        histWireMock = new WireMockServer(wireMockConfig().dynamicPort());
        edgeWireMock = new WireMockServer(wireMockConfig().dynamicPort());

        acsWireMock.start();
        pacWireMock.start();
        evntWireMock.start();
        histWireMock.start();
        edgeWireMock.start();

        // Serve JWKS no acsWireMock para validacao JWT pelo Spring Security
        stubJwks(acsWireMock);
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("dash.acs.base-url", () -> "http://localhost:" + acsWireMock.port());
        registry.add("dash.pac.base-url", () -> "http://localhost:" + pacWireMock.port());
        registry.add("dash.evnt.base-url", () -> "http://localhost:" + evntWireMock.port());
        registry.add("dash.hist.base-url", () -> "http://localhost:" + histWireMock.port());
        registry.add("dash.edge.base-url", () -> "http://localhost:" + edgeWireMock.port());
        // JWK endpoint served by acsWireMock
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:" + acsWireMock.port() + "/.well-known/jwks.json");
    }

    @AfterAll
    static void stopWireMocks() {
        acsWireMock.stop();
        pacWireMock.stop();
        evntWireMock.stop();
        histWireMock.stop();
        edgeWireMock.stop();
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected PanelSessionRegistry panelSessionRegistry;

    @BeforeEach
    void resetAllWireMocks() {
        acsWireMock.resetAll();
        pacWireMock.resetAll();
        evntWireMock.resetAll();
        histWireMock.resetAll();
        edgeWireMock.resetAll();
        // Re-stub JWKS apos reset
        stubJwks(acsWireMock);
        // Fecha todas as sessoes SSE abertas por testes anteriores
        // (PanelSessionRegistry e stateful in-memory)
    }

    // ---- helpers ----

    private static void stubJwks(WireMockServer server) {
        try {
            RSAKey rsaKey = new RSAKey.Builder(TEST_RSA_PUBLIC_KEY).keyID("test-key-id").build();
            String jwks = new JWKSet(rsaKey).toString();
            server.stubFor(get("/.well-known/jwks.json").willReturn(okJson(jwks)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to stub JWKS", e);
        }
    }
}
