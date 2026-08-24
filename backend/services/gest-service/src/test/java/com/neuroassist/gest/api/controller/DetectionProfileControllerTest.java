package com.neuroassist.gest.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neuroassist.gest.api.dto.DetectionProfilePrimitiveResponse;
import com.neuroassist.gest.api.dto.DetectionProfileResponse;
import com.neuroassist.gest.domain.model.DetectionThresholdTable;
import com.neuroassist.gest.domain.model.Sensibilidade;
import com.neuroassist.gest.application.port.in.DetectionProfileUseCase;
import com.neuroassist.gest.application.port.out.EdgePairingPort;
import com.neuroassist.gest.infrastructure.security.CorrelationIdInterceptor;
import com.neuroassist.gest.shared.error.CodedException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Unit tests for {@link DetectionProfileController} — ETag/304 conditional-GET logic (P4,
 * GEST-016). Uses {@code @WebMvcTest} slice (no full Spring Boot context): the security
 * auto-configuration is excluded and {@code SecurityConfig} (which carries
 * {@code @EnableMethodSecurity}) is not part of the slice, so the controller's
 * {@code @PreAuthorize} is not evaluated here. Authorization is covered by {@code SecurityIT} and
 * the RBAC BDD scenarios.
 */
@WebMvcTest(
        value = DetectionProfileController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class
        })
class DetectionProfileControllerTest {

    private static final String BASE_PATH = "/gest-service/detection-profile";

    private MockMvc mockMvc;

    @MockBean private DetectionProfileUseCase detectionProfileUseCase;

    @MockBean private EdgePairingPort edgePairingPort;

    // Required by WebMvcConfig which registers it as a HandlerInterceptor
    @MockBean private CorrelationIdInterceptor correlationIdInterceptor;

    // Required by MultiTenantConnectionProvider (@Component picked up by WebMvcTest slice)
    @MockBean private DataSource dataSource;

    @Autowired
    void setMockMvc(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @BeforeEach
    void setUp() throws Exception {
        // preHandle must return true; Mockito default for boolean is false which would block
        // requests
        when(correlationIdInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    // ── publish — corpo recusado pelo dominio (E2E-033) ──────────────────────

    /**
     * O 400 tem de dizer O QUE esta errado. A validacao vive no construtor compacto do record, e
     * portanto dispara durante a desserializacao do Jackson; sem o handler dedicado, o Spring
     * responde "Failed to read request" e a mensagem util fica enterrada na causa.
     */
    @Test
    void publish_tabelaComAltaMenosSensivelQueBaixa_explicaOMotivo() throws Exception {
        String invertida =
                """
                {"thresholds":{
                 "baixa":{"earClose":0.25,"earOpen":0.30,"earOtherMax":0.33,"blinkMinFrames":3,"doubleBlinkWindowMs":1500,"briRaiseOn":0.34,"briRaiseOff":0.26,"briOtherMax":0.29,"browMinFrames":7},
                 "media":{"earClose":0.22,"earOpen":0.27,"earOtherMax":0.30,"blinkMinFrames":2,"doubleBlinkWindowMs":2000,"briRaiseOn":0.30,"briRaiseOff":0.22,"briOtherMax":0.25,"browMinFrames":5},
                 "alta":{"earClose":0.19,"earOpen":0.24,"earOtherMax":0.27,"blinkMinFrames":2,"doubleBlinkWindowMs":2500,"briRaiseOn":0.26,"briRaiseOff":0.18,"briOtherMax":0.21,"browMinFrames":3}}}
                """;

        mockMvc.perform(
                        post(BASE_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invertida))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("earClose")))
                .andExpect(jsonPath("$.detail").value(containsString("crescer")));
    }

    /** JSON quebrado nao ganha a mensagem do parser — ela nao ajuda quem chamou. */
    @Test
    void publish_jsonMalformado_mantemMensagemGenerica() throws Exception {
        mockMvc.perform(
                        post(BASE_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"thresholds\": {{{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Failed to read request"));
    }

    // ── findActive — 200 with ETag ────────────────────────────────────────────

    @Test
    void findActive_returnsOk_withEtagAndCacheControl() throws Exception {
        DetectionProfileResponse profile = buildProfile("\"v3\"");
        when(detectionProfileUseCase.findActive()).thenReturn(Optional.of(profile));

        mockMvc.perform(get(BASE_PATH + "/active"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v3\""))
                .andExpect(header().string("Cache-Control", "max-age=60"));
    }

    // ── findActive — 304 when If-None-Match matches ───────────────────────────

    @Test
    void findActive_returns304_whenEtagMatches() throws Exception {
        DetectionProfileResponse profile = buildProfile("\"v3\"");
        when(detectionProfileUseCase.findActive()).thenReturn(Optional.of(profile));

        mockMvc.perform(get(BASE_PATH + "/active").header("If-None-Match", "\"v3\""))
                .andExpect(status().isNotModified());
    }

    // ── findActive — 200 when If-None-Match does NOT match ───────────────────

    @Test
    void findActive_returns200_whenEtagDoesNotMatch() throws Exception {
        DetectionProfileResponse profile = buildProfile("\"v3\"");
        when(detectionProfileUseCase.findActive()).thenReturn(Optional.of(profile));

        mockMvc.perform(get(BASE_PATH + "/active").header("If-None-Match", "\"v2\""))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v3\""));
    }

    // ── findForDevice — 200 with ETag when device is paired ──────────────────

    @Test
    void findForDevice_returnsOk_withEtagWhenPaired() throws Exception {
        UUID deviceId = UUID.randomUUID();
        DetectionProfileResponse profile = buildProfile("\"v5\"");
        // edgePairingPort returns normally (device paired)
        when(detectionProfileUseCase.findForDevice(deviceId)).thenReturn(Optional.of(profile));

        mockMvc.perform(get(BASE_PATH + "/devices/" + deviceId))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v5\""))
                .andExpect(header().string("Cache-Control", "max-age=60"));
    }

    // ── findForDevice — 304 when ETag matches ────────────────────────────────

    @Test
    void findForDevice_returns304_whenEtagMatches() throws Exception {
        UUID deviceId = UUID.randomUUID();
        DetectionProfileResponse profile = buildProfile("\"v5\"");
        when(detectionProfileUseCase.findForDevice(deviceId)).thenReturn(Optional.of(profile));

        mockMvc.perform(get(BASE_PATH + "/devices/" + deviceId).header("If-None-Match", "\"v5\""))
                .andExpect(status().isNotModified());
    }

    // ── findForDevice — 403 when device is not paired ────────────────────────

    @Test
    void findForDevice_returns403_whenDeviceNotPaired() throws Exception {
        UUID deviceId = UUID.randomUUID();
        doThrow(new CodedException("device-not-paired", "Device not paired: " + deviceId))
                .when(edgePairingPort)
                .checkPairingAndGetTenant(deviceId);

        mockMvc.perform(get(BASE_PATH + "/devices/" + deviceId)).andExpect(status().isForbidden());
    }

    // ── findForDevice — 503 when edge is down ────────────────────────────────

    @Test
    void findForDevice_returns503_whenEdgeDown() throws Exception {
        UUID deviceId = UUID.randomUUID();
        doThrow(new CodedException("edge-unavailable", "Edge service unavailable"))
                .when(edgePairingPort)
                .checkPairingAndGetTenant(deviceId);

        mockMvc.perform(get(BASE_PATH + "/devices/" + deviceId))
                .andExpect(status().isServiceUnavailable());
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private static DetectionProfileResponse buildProfile(String etag) {
        return new DetectionProfileResponse(
                UUID.randomUUID(),
                3L,
                true,
                "v1.1.0",
                DetectionThresholdTable.defaults(),
                List.of(
                        new DetectionProfilePrimitiveResponse(
                                UUID.randomUUID(),
                                "Piscar",
                                "ref/piscar-v1",
                                Sensibilidade.media,
                                DetectionProfilePrimitiveResponse.Origem.PERFIL_EMPRESA)),
                null,
                null,
                false,
                Instant.now(),
                etag);
    }
}
