package com.neuroassist.pac.security;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neuroassist.pac.application.port.in.CatalogoAtivoUseCase;
import com.neuroassist.pac.application.port.in.ComandoAuthoringUseCase;
import com.neuroassist.pac.application.port.in.ComandoLifecycleUseCase;
import com.neuroassist.pac.application.port.in.PacienteUseCase;
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
 * Security configuration tests for PAC-021 (P7 — SPEC-06).
 *
 * <p>Verifies:
 *
 * <ul>
 *   <li>401 on protected endpoints without token
 *   <li>403 with wrong scope (@PreAuthorize enforcement)
 *   <li>200/201 with correct scope
 *   <li>GET /actuator/health accessible without Bearer token
 *   <li>All error responses carry Content-Type: application/problem+json
 *   <li>X-Correlation-Id echoed in every response
 *   <li>Tenant mismatch (JWT empresaId ≠ X-Empresa-Id header) → 403
 * </ul>
 *
 * <p>Uses {@code @SpringBootTest(webEnvironment=MOCK)} so that Actuator endpoints are loaded (not
 * available with {@code @WebMvcTest}) and {@code @EnableMethodSecurity} proxy is active for
 * {@code @PreAuthorize} enforcement.
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
    // MockMvc + mocked beans (no real Keycloak or service dependencies)
    // ---------------------------------------------------------------------------

    @Autowired MockMvc mvc;

    // JwtDecoder mocked so no real Keycloak is needed
    @MockitoBean JwtDecoder jwtDecoder;

    @MockitoBean PacienteUseCase pacienteUseCase;
    @MockitoBean ComandoAuthoringUseCase comandoAuthoringUseCase;
    @MockitoBean ComandoLifecycleUseCase comandoLifecycleUseCase;
    @MockitoBean CatalogoAtivoUseCase catalogoAtivoUseCase;

    // ---- 401 — no token ----

    @Test
    @DisplayName("POST /pacientes sem token retorna 401")
    void createPaciente_semToken_retorna401() throws Exception {
        mvc.perform(
                        post("/api/pac/v1/pacientes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("GET /pacientes sem token retorna 401")
    void listPacientes_semToken_retorna401() throws Exception {
        mvc.perform(get("/api/pac/v1/pacientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("GET /pacientes/{id} sem token retorna 401")
    void getPaciente_semToken_retorna401() throws Exception {
        mvc.perform(get("/api/pac/v1/pacientes/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ---- 403 — scope errado (@PreAuthorize enforcement) ----

    @Test
    @DisplayName("POST /pacientes com scope errado retorna 403 /errors/insufficient-scope")
    void createPaciente_scopeErrado_retorna403() throws Exception {
        mvc.perform(
                        post("/api/pac/v1/pacientes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"nome\":\"Paciente\",\"empresaId\":\""
                                                + UUID.randomUUID()
                                                + "\"}")
                                .with(
                                        jwt().authorities(
                                                        // pac.paciente.read is wrong for POST
                                                        new SimpleGrantedAuthority(
                                                                "pac.paciente.read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /pacientes/{id} com scope read-only retorna 403")
    void updatePaciente_scopeErrado_retorna403() throws Exception {
        mvc.perform(
                        put("/api/pac/v1/pacientes/" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "pac.paciente.read"))))
                .andExpect(status().isForbidden());
    }

    // ---- 403 — tenant mismatch ----

    @Test
    @DisplayName(
            "Request com JWT empresaId diferente de X-Empresa-Id retorna 403 /errors/tenant-mismatch")
    void tenantMismatch_retorna403() throws Exception {
        String jwtEmpresaId = UUID.randomUUID().toString();
        String headerEmpresaId = UUID.randomUUID().toString(); // different UUID

        mvc.perform(
                        get("/api/pac/v1/pacientes")
                                .header("X-Empresa-Id", headerEmpresaId)
                                .with(
                                        jwt().jwt(
                                                        jwtBuilder ->
                                                                jwtBuilder.claim(
                                                                        "empresaId", jwtEmpresaId))
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "pac.paciente.read"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/errors/tenant-mismatch"));
    }

    @Test
    @DisplayName("Request com JWT empresaId igual a X-Empresa-Id nao retorna 403 mismatch")
    void tenantMatch_naoRetorna403Mismatch() throws Exception {
        String empresaId = UUID.randomUUID().toString();

        when(pacienteUseCase.listAll()).thenReturn(java.util.List.of());

        mvc.perform(
                        get("/api/pac/v1/pacientes")
                                .header("X-Empresa-Id", empresaId)
                                .with(
                                        jwt().jwt(
                                                        jwtBuilder ->
                                                                jwtBuilder.claim(
                                                                        "empresaId", empresaId))
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "pac.paciente.read"))))
                .andExpect(status().isOk());
    }

    // ---- Actuator: permitAll ----

    @Test
    @DisplayName("GET /actuator/health sem token retorna 200 (permitAll)")
    void actuatorHealth_semToken_retorna200() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // ---- X-Correlation-Id header ----

    @Test
    @DisplayName("X-Correlation-Id ausente na request gera um novo na response de 401")
    void correlationId_geradoSeAusente_em401() throws Exception {
        mvc.perform(get("/api/pac/v1/pacientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    @DisplayName("X-Correlation-Id da request e ecoado na response de 401")
    void correlationId_ecoado_em401() throws Exception {
        mvc.perform(get("/api/pac/v1/pacientes").header("X-Correlation-Id", "pac-test-corr-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "pac-test-corr-123"));
    }

    // ---- RFC 7807 ProblemDetail structure ----

    @Test
    @DisplayName("401 response contem campo error.correlationId no body")
    void unauthorized_body_temCorrelationId() throws Exception {
        mvc.perform(get("/api/pac/v1/pacientes").header("X-Correlation-Id", "corr-001"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error.correlationId").value("corr-001"));
    }

    @Test
    @DisplayName("Tenant mismatch response contem correlationId no body")
    void tenantMismatch_body_temCorrelationId() throws Exception {
        String jwtEmpresaId = UUID.randomUUID().toString();
        String headerEmpresaId = UUID.randomUUID().toString();

        mvc.perform(
                        get("/api/pac/v1/pacientes")
                                .header("X-Empresa-Id", headerEmpresaId)
                                .header("X-Correlation-Id", "corr-tenant-002")
                                .with(
                                        jwt().jwt(b -> b.claim("empresaId", jwtEmpresaId))
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "pac.paciente.read"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.error.correlationId").value("corr-tenant-002"))
                .andExpect(jsonPath("$.error.code").value("tenant-mismatch"));
    }
}
