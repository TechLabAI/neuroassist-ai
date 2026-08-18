package com.neuroassist.edge.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code tenant_id} claim from the already-validated JWT and binds it to {@link
 * TenantContextHolder} for the duration of the request.
 *
 * <p>This filter is registered INSIDE Spring Security's {@code FilterChainProxy} (via {@code
 * SecurityConfig.addFilterAfter(..., BearerTokenAuthenticationFilter.class)}), so the
 * SecurityContext is guaranteed to be populated when this filter runs. It is NOT registered as a
 * standalone Servlet filter (see {@code SecurityConfig.tenantContextFilterRegistration}).
 *
 * <p>Validates the optional {@code X-Tenant-Id} header against the JWT claim: if present and
 * mismatched, the request is rejected with 403 to prevent tenant spoofing.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String JWT_CLAIM_TENANT_ID = "tenant_id";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String tenantId = extractTenantIdFromJwt();
        if (tenantId != null) {
            String headerTenant = request.getHeader(HEADER_TENANT_ID);
            if (headerTenant != null && !headerTenant.isBlank() && !headerTenant.equals(tenantId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\":\"tenant_mismatch\"}");
                return;
            }
            TenantContextHolder.set(tenantId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private String extractTenantIdFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            if (jwt != null) {
                Object claim = jwt.getClaim(JWT_CLAIM_TENANT_ID);
                return claim == null ? null : claim.toString();
            }
        }
        return null;
    }
}
