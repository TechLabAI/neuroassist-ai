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
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * P5 (GEST-018): Reads tenantId from JWT claims ("tenant" first, fallback to "sub") and binds it to
 * {@link TenantContextHolder} for the duration of the request.
 *
 * <p>Registered inside the Spring Security filter chain via {@code addFilterAfter(...,
 * BearerTokenAuthenticationFilter.class)} in {@link
 * com.neuroassist.gest.infrastructure.config.SecurityConfig} — NOT auto-registered as a standalone
 * servlet filter (disabled via FilterRegistrationBean).
 *
 * <p>P3 addition (GEST-014): Guard 503 — if the JWT carries a tenantId whose corresponding schema
 * ({@code t_<tenantId>}) does not exist in the database, the filter short-circuits with HTTP 503
 * and a RFC-7807 ProblemDetail body before the request reaches any controller.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String JWT_CLAIM_TENANT = "tenant";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    private static final String MDC_CORRELATION_KEY = "correlationId";
    private static final String SCHEMA_PREFIX = "t_";
    private static final String PROBLEM_TYPE_TENANT_NOT_FOUND = "tenant-not-found";

    private static final String SCHEMA_EXISTS_SQL =
            "SELECT 1 FROM information_schema.schemata WHERE schema_name = ?";

    private final DataSource dataSource;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // ── X-Correlation-Id — generate if absent, propagate in response ──────
        String correlationId = request.getHeader(HEADER_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_CORRELATION_KEY, correlationId);
        response.setHeader(HEADER_CORRELATION_ID, correlationId);

        // ── Tenant extraction from JWT ─────────────────────────────────────────
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
            String schemaName = resolveSchemaName(tenantId);
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
            MDC.put(TenantContextHolder.MDC_KEY, tenantId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            MDC.remove(TenantContextHolder.MDC_KEY);
            MDC.remove(MDC_CORRELATION_KEY);
        }
    }

    /**
     * Extracts the tenant identifier from the current request's JWT. Reads claim "tenant" first;
     * falls back to "sub" (subject) if absent.
     */
    private String extractTenantIdFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            if (jwt != null) {
                String tenantClaim = jwt.getClaimAsString(JWT_CLAIM_TENANT);
                if (tenantClaim != null && !tenantClaim.isBlank()) {
                    return tenantClaim;
                }
                // Fallback to subject
                String sub = jwt.getSubject();
                if (sub != null && !sub.isBlank()) {
                    return sub;
                }
            }
        }
        return null;
    }

    /**
     * Resolves the Postgres schema that holds the tenant's data.
     *
     * <p>ACS records the provisioned schema in {@code neuroassist_global.tenant.schema_nome} and
     * issues the tenant UUID in the JWT, so the schema cannot be derived from the identifier. This
     * mirrors {@link MultiTenantConnectionProvider}, which routes the {@code search_path} the same
     * way — the guard here must agree with it or it rejects tenants that would route fine (E2E-009
     * / C-14).
     *
     * <p>Falls back to the legacy {@code t_<tenantId>} convention when the tenant catalogue has no
     * row, which is the shape the test suites provision.
     */
    private String resolveSchemaName(String tenantId) {
        String sql =
                "SELECT schema_nome FROM neuroassist_global.tenant"
                        + " WHERE tenant_id::text = ? AND schema_nome IS NOT NULL";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            log.debug(
                    "Could not resolve schema_nome for tenant {} ({}); falling back to {}{}",
                    tenantId,
                    e.getMessage(),
                    SCHEMA_PREFIX,
                    tenantId);
        }
        return SCHEMA_PREFIX + tenantId;
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
}
