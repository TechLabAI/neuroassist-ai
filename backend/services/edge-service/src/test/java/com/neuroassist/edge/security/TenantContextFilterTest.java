package com.neuroassist.edge.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.neuroassist.edge.infrastructure.security.TenantContextFilter;
import com.neuroassist.edge.infrastructure.security.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Unit tests for TenantContextFilter (EDGE-013, P5).
 *
 * <p>Verifies: - tenantId derived from JWT claim, never from body - TenantContextHolder.clear() in
 * finally (no ThreadLocal leak) - X-Tenant-Id header mismatch triggers 403 - No JWT: tenant not
 * set, request proceeds normally
 */
class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter();

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("tenantId derivado do claim JWT — nunca do body")
    void tenantId_derivadoDoJwt() throws Exception {
        setJwtInSecurityContext("tenant-abc-123");
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain =
                (request, response) -> {
                    assertThat(TenantContextHolder.getTenantIdOrNull()).isEqualTo("tenant-abc-123");
                };

        filter.doFilter(req, res, chain);
    }

    @Test
    @DisplayName("TenantContextHolder limpo no finally — sem leak entre requests")
    void tenantContextHolder_limpoNoFinally() throws Exception {
        setJwtInSecurityContext("tenant-cleanup-test");
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> {}; // do nothing

        filter.doFilter(req, res, chain);

        assertThat(TenantContextHolder.getTenantIdOrNull()).isNull();
    }

    @Test
    @DisplayName("TenantContextHolder limpo no finally mesmo com excecao no chain")
    void tenantContextHolder_limpoNoFinally_comExcecao() throws Exception {
        setJwtInSecurityContext("tenant-error-case");
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain =
                (request, response) -> {
                    throw new RuntimeException("chain failure");
                };

        try {
            filter.doFilter(req, res, chain);
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(TenantContextHolder.getTenantIdOrNull()).isNull();
    }

    @Test
    @DisplayName("Header X-Tenant-Id diferente do JWT retorna 403 tenant_mismatch")
    void headerTenantId_divergente_retorna403() throws Exception {
        setJwtInSecurityContext("tenant-from-jwt");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Tenant-Id", "tenant-spoofed");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        verifyNoInteractions(chain);
        // tenant context must be cleared even on 403
        assertThat(TenantContextHolder.getTenantIdOrNull()).isNull();
    }

    @Test
    @DisplayName("Header X-Tenant-Id igual ao JWT nao bloqueia request")
    void headerTenantId_igual_naoBloqueia() throws Exception {
        setJwtInSecurityContext("tenant-match");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Tenant-Id", "tenant-match");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain =
                (request, response) -> {
                    assertThat(TenantContextHolder.getTenantIdOrNull()).isEqualTo("tenant-match");
                };

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200); // not blocked
    }

    @Test
    @DisplayName("Sem JWT no SecurityContext: tenant nao definido, chain continua")
    void semJwt_tenantNaoDefinido_chainContinua() throws Exception {
        // No auth in SecurityContext
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain =
                (request, response) -> {
                    // tenant not set — enroll endpoints don't need it
                    assertThat(TenantContextHolder.getTenantIdOrNull()).isNull();
                };

        filter.doFilter(req, res, chain);
    }

    // ---- Helpers ----

    private static void setJwtInSecurityContext(String tenantId) {
        Jwt jwt =
                Jwt.withTokenValue("token")
                        .header("alg", "RS256")
                        .claims(c -> c.putAll(Map.of("sub", "device-001", "tenant_id", tenantId)))
                        .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
