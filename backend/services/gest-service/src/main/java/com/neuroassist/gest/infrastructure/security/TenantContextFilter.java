package com.neuroassist.gest.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads tenantId from JWT claim ("tenant_id") and binds it to {@link TenantContextHolder} for the
 * duration of the request. Validates X-Tenant-Id header (if present) matches the JWT claim.
 *
 * <p>P3 addition (GEST-014): Guard 503 — if the JWT carries a tenantId whose corresponding schema
 * ({@code t_<tenantId>}) does not exist in the database, the filter short-circuits with HTTP 503
 * and a RFC-7807 ProblemDetail body before the request reaches any controller.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String JWT_CLAIM_TENANT_ID = "tenant_id";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String SCHEMA_PREFIX = "t_";
    private static final String PROBLEM_TYPE_TENANT_NOT_FOUND = "tenant-not-found";

    private static final String SCHEMA_EXISTS_SQL =
            "SELECT 1 FROM information_schema.schemata WHERE schema_name = ?";

    private final DataSource dataSource;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String tenantId = extractTenantIdFromJwt();

        if (tenantId != null) {
            // Validate X-Tenant-Id header — must match JWT claim when present.
            String headerTenant = request.getHeader(HEADER_TENANT_ID);
            if (headerTenant != null && !headerTenant.isBlank() && !headerTenant.equals(tenantId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"tenant_mismatch\"}");
                return;
            }

            // Guard 503: schema must exist before binding the context (GEST-014).
            String schemaName = SCHEMA_PREFIX + tenantId;
            if (!schemaExists(schemaName)) {
                log.warn(
                        "Tenant schema '{}' not found in database — returning 503 (tenantId={})",
                        schemaName,
                        tenantId);
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/problem+json");
                response.getWriter()
                        .write(
                                String.format(
                                        "{"
                                                + "\"type\":\"%s\","
                                                + "\"status\":503,"
                                                + "\"title\":\"Tenant Schema Not Found\","
                                                + "\"detail\":\"Schema for tenant '%s' does not"
                                                + " exist or is currently unavailable\""
                                                + "}",
                                        PROBLEM_TYPE_TENANT_NOT_FOUND, tenantId));
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

    /**
     * Checks whether the given schema name exists in the connected PostgreSQL instance using the
     * standard ANSI {@code information_schema.schemata} view. Fails-safe: any {@link SQLException}
     * is logged and the method returns {@code false}, causing a 503 to be returned (defensive
     * posture — better to reject than to route to a missing schema).
     */
    private boolean schemaExists(String schemaName) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(SCHEMA_EXISTS_SQL)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error(
                    "Failed to verify schema existence for '{}' — treating as absent: {}",
                    schemaName,
                    e.getMessage());
            return false;
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
