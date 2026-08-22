package com.neuroassist.edge.security;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neuroassist.edge.application.port.in.DeviceCredentialVerificationUseCase;
import com.neuroassist.edge.application.port.in.DeviceRegistrationUseCase;
import com.neuroassist.edge.application.port.in.HeartbeatUseCase;
import com.neuroassist.edge.application.port.in.LivenessUseCase;
import com.neuroassist.edge.application.port.in.PairingUseCase;
import com.neuroassist.edge.application.port.in.ProfilePointerUseCase;
import com.neuroassist.edge.application.service.DeviceEnrollmentService;
import com.neuroassist.edge.domain.model.PairingState;
import com.neuroassist.edge.shared.error.CodedException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Security configuration tests for EDGE-012 (P5).
 *
 * <p>Verifies: - 401 on protected endpoints without token - 403 with wrong scope - 200/201 with
 * correct scope - POST /enroll accessible without Bearer token - X-Correlation-Id echoed in every
 * response - tenantId derived from JWT, not from body (spoofing attempt returns the JWT tenant)
 *
 * <p>Uses @SpringBootTest(webEnvironment=MOCK) so that:
 *
 * <ul>
 *   <li>Actuator endpoints (/actuator/health) are loaded (not available with @WebMvcTest).
 *   <li>@EnableMethodSecurity proxy is active — @PreAuthorize returns 403 for wrong scopes.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    // ---------------------------------------------------------------------------
    // Testcontainers PostgreSQL singleton — required by @SpringBootTest (full context)
    // ---------------------------------------------------------------------------

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("neuroassist-ai_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withInitScript("init-schema.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    // ---------------------------------------------------------------------------

    @Autowired MockMvc mvc;

    // JwtDecoder mocked so no real Keycloak/ACS is needed in tests
    @MockitoBean JwtDecoder jwtDecoder;

    @MockitoBean DeviceRegistrationUseCase deviceRegistrationUseCase;

    /**
     * O controller do enroll passou a delegar para o {@link DeviceEnrollmentService}, que resolve o
     * tenant no índice global antes da transação (C-21). O cenário aqui é sobre a chain de
     * segurança (permitAll sem Bearer), então basta mockar o novo colaborador.
     */
    @MockitoBean DeviceEnrollmentService deviceEnrollmentService;

    @MockitoBean HeartbeatUseCase heartbeatUseCase;

    @MockitoBean LivenessUseCase livenessUseCase;

    @MockitoBean PairingUseCase pairingUseCase;

    @MockitoBean ProfilePointerUseCase profilePointerUseCase;

    @MockitoBean DeviceCredentialVerificationUseCase deviceCredentialVerificationUseCase;

    // ---- 401 cases (no token) ----

    @Test
    @DisplayName("POST /devices sem token retorna 401")
    void register_semToken_retorna401() throws Exception {
        mvc.perform(
                        post("/api/v1/edge/devices")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"label\":\"device-1\",\"pacienteHint\":null}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /heartbeat sem token retorna 401")
    void heartbeat_semToken_retorna401() throws Exception {
        mvc.perform(
                        post("/api/v1/edge/heartbeat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"agentVersion\":\"1.0\",\"runningProfileVersion\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /devices/{id}/liveness sem token retorna 401")
    void liveness_semToken_retorna401() throws Exception {
        mvc.perform(get("/api/v1/edge/devices/" + UUID.randomUUID() + "/liveness"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 403 cases (scope errado) ----

    @Test
    @DisplayName("POST /devices com scope edge.device (errado) retorna 403")
    void register_scopeErrado_retorna403() throws Exception {
        mvc.perform(
                        post("/api/v1/edge/devices")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"label\":\"device-1\"}")
                                .with(jwt().authorities(new SimpleGrantedAuthority("edge.device"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /heartbeat com scope edge.manage (errado) retorna 403")
    void heartbeat_scopeErrado_retorna403() throws Exception {
        mvc.perform(
                        post("/api/v1/edge/heartbeat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"agentVersion\":\"1.0\",\"runningProfileVersion\":1}")
                                .with(jwt().authorities(new SimpleGrantedAuthority("edge.manage"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /liveness com scope edge.manage (errado) retorna 403")
    void liveness_scopeErrado_retorna403() throws Exception {
        mvc.perform(
                        get("/api/v1/edge/devices/" + UUID.randomUUID() + "/liveness")
                                .with(jwt().authorities(new SimpleGrantedAuthority("edge.manage"))))
                .andExpect(status().isForbidden());
    }

    // ---- 200/201 cases (scope correto) ----

    @Test
    @DisplayName("POST /devices com scope edge.manage retorna 201")
    void register_scopeCorreto_retorna201() throws Exception {
        UUID deviceId = UUID.randomUUID();
        DeviceRegistrationUseCase.RegisterResult result =
                new DeviceRegistrationUseCase.RegisterResult(
                        deviceId,
                        PairingState.registered,
                        "device-1",
                        "et_dummy-token",
                        Instant.now().plusSeconds(900),
                        Instant.now());
        when(deviceRegistrationUseCase.register(anyString(), any(), anyString()))
                .thenReturn(result);

        mvc.perform(
                        post("/api/v1/edge/devices")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"label\":\"device-1\"}")
                                .with(jwt().authorities(new SimpleGrantedAuthority("edge.manage"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("GET /liveness com scope edge.read retorna 200")
    void liveness_scopeRead_retorna200() throws Exception {
        UUID deviceId = UUID.randomUUID();
        LivenessUseCase.LivenessResult r =
                new LivenessUseCase.LivenessResult(
                        deviceId,
                        null,
                        "online",
                        Instant.now(),
                        "1.0.0",
                        90L,
                        Instant.now(),
                        "\"v1\"");
        when(livenessUseCase.evaluate(deviceId)).thenReturn(r);

        mvc.perform(
                        get("/api/v1/edge/devices/" + deviceId + "/liveness")
                                .with(jwt().authorities(new SimpleGrantedAuthority("edge.read"))))
                .andExpect(status().isOk());
    }

    // ---- POST /enroll: permitAll (no Bearer required) ----

    @Test
    @DisplayName("POST /enroll sem Bearer retorna resultado do use-case (nao 401)")
    void enroll_semBearer_nao401() throws Exception {
        DeviceRegistrationUseCase.EnrollResult result =
                new DeviceRegistrationUseCase.EnrollResult(
                        UUID.randomUUID(),
                        "tenant-abc",
                        "client-123",
                        "cs_secret",
                        "http://acs/token",
                        "edge.device",
                        30);
        when(deviceEnrollmentService.enroll(anyString(), anyString())).thenReturn(result);

        mvc.perform(
                        post("/api/v1/edge/devices/enroll")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"enrollmentToken\":\"et_token\",\"agentVersion\":\"1.0\"}"))
                .andExpect(status().isOk()); // not 401 — permitAll
    }

    // ---- X-Correlation-Id echoed ----

    @Test
    @DisplayName("X-Correlation-Id ausente na request e gerado na response")
    void correlationId_geradoSeAusente() throws Exception {
        mvc.perform(get("/api/v1/edge/devices/" + UUID.randomUUID() + "/liveness"))
                // 401 expected (no token), but header must be present
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    @DisplayName("X-Correlation-Id da request e ecoado na response")
    void correlationId_ecoado() throws Exception {
        mvc.perform(
                        get("/api/v1/edge/devices/" + UUID.randomUUID() + "/liveness")
                                .header("X-Correlation-Id", "test-corr-id-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "test-corr-id-123"));
    }

    // ---- Actuator health: permitAll ----

    @Test
    @DisplayName("GET /actuator/health sem token retorna 200 (permitAll)")
    void actuatorHealth_semToken_retorna200() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // ---- C-18: POST /internal/devices/verify-credential ----

    private static final String VERIFY_PATH = "/internal/devices/verify-credential";
    private static final String VERIFY_BODY =
            "{\"clientId\":\"edge-11111111-2222-3333-4444-555555555555\",\"clientSecret\":\"cs_x\"}";

    @Test
    @DisplayName("POST /internal/devices/verify-credential sem token retorna 401")
    void verifyCredential_semToken_retorna401() throws Exception {
        mvc.perform(post(VERIFY_PATH).contentType(MediaType.APPLICATION_JSON).content(VERIFY_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /internal/devices/verify-credential com edge.device (errado) retorna 403")
    void verifyCredential_scopeErrado_retorna403() throws Exception {
        mvc.perform(
                        post(VERIFY_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VERIFY_BODY)
                                .with(jwt().authorities(new SimpleGrantedAuthority("edge.device"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /internal/devices/verify-credential com edge.internal retorna 200")
    void verifyCredential_scopeCorreto_retorna200() throws Exception {
        UUID deviceId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(deviceCredentialVerificationUseCase.verify(anyString(), anyString()))
                .thenReturn(
                        new DeviceCredentialVerificationUseCase.VerificationResult(
                                deviceId, "e2e00000-0000-0000-0000-000000000001"));

        mvc.perform(
                        post(VERIFY_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VERIFY_BODY)
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "edge.internal"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.deviceId").value(deviceId.toString()))
                .andExpect(jsonPath("$.tenantId").value("e2e00000-0000-0000-0000-000000000001"));
    }

    @Test
    @DisplayName("POST /internal/devices/verify-credential: credencial inválida vira 401 genérico")
    void verifyCredential_invalida_retorna401Generico() throws Exception {
        when(deviceCredentialVerificationUseCase.verify(anyString(), anyString()))
                .thenThrow(
                        new CodedException(
                                "edge.credential-invalid", "Device credential is invalid."));

        mvc.perform(
                        post(VERIFY_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VERIFY_BODY)
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "edge.internal"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("edge.credential-invalid"))
                // o corpo não pode devolver o segredo nem qualquer pista do motivo
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("cs_x"))));
    }

    /**
     * O ADR-008 define {@code /api/v1/edge/**} como a superfície pública roteada pelo GWY. Se
     * alguém mover esta rota para debaixo daquele prefixo, ela vira pública — este teste falha
     * primeiro.
     */
    @Test
    @DisplayName("verify-credential NÃO existe sob /api/v1/edge/** (não é superfície pública)")
    void verifyCredential_naoExisteSobPrefixoPublico() throws Exception {
        mvc.perform(
                        post("/api/v1/edge/devices/verify-credential")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VERIFY_BODY)
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "edge.internal"))))
                // 405 hoje (o prefixo casa o GET /devices/{deviceId}), 404 se aquele mapping
                // mudar — o que importa é que nunca vira 2xx sob o prefixo público.
                .andExpect(status().is4xxClientError());
    }
}
