package com.neuroassist.gest.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neuroassist.gest.api.dto.DetectionProfileResponse;
import com.neuroassist.gest.application.port.in.DetectionProfileUseCase;
import com.neuroassist.gest.application.port.out.EdgePairingPort;
import com.neuroassist.gest.infrastructure.security.CorrelationIdInterceptor;
import com.neuroassist.gest.shared.error.CodedException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
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
 * GEST-016). Uses {@code @WebMvcTest} slice (no full Spring Boot context). Security is {@code
 * permitAll()} in the current PoC config.
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
                "v1.0.0",
                Map.of("threshold", 0.85),
                Instant.now(),
                etag);
    }
}
