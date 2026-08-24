package com.neuroassist.edge.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
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
 *
 * <p>P7 addition (EDGE-018): also populates MDC keys {@code tenantId} and {@code actor} (JWT {@code
 * sub} claim) for structured logging. Cleared in {@code finally} to prevent ThreadLocal leak across
 * pooled threads.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String JWT_CLAIM_TENANT_ID = "tenant_id";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    // MDC keys (EDGE-018, P7) — must match logback-spring.xml MDC fields
    static final String MDC_TENANT_ID = "tenantId";
    static final String MDC_ACTOR = "actor";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        JwtClaims claims = extractClaimsFromJwt();
        if (claims.tenantId() != null) {
            String headerTenant = request.getHeader(HEADER_TENANT_ID);
            if (headerTenant != null
                    && !headerTenant.isBlank()
                    && !headerTenant.equals(claims.tenantId())) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\":\"tenant_mismatch\"}");
                return;
            }
            TenantContextHolder.set(claims.tenantId());
            MDC.put(MDC_TENANT_ID, claims.tenantId());
        } else if (requiresTenantClaim()) {
            writeMissingTenantClaim(response);
            return;
        }
        if (claims.actor() != null) {
            MDC.put(MDC_ACTOR, claims.actor());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            MDC.remove(MDC_TENANT_ID);
            MDC.remove(MDC_ACTOR);
        }
    }

    private JwtClaims extractClaimsFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            if (jwt != null) {
                Object tenantClaim = jwt.getClaim(JWT_CLAIM_TENANT_ID);
                Object subClaim = jwt.getClaim("sub");
                String tenantId = tenantClaim == null ? null : tenantClaim.toString();
                String actor = subClaim == null ? null : subClaim.toString();
                return new JwtClaims(tenantId, actor);
            }
        }
        return new JwtClaims(null, null);
    }

    /** Value object for extracted JWT claims (tenantId + actor/sub). */
    private record JwtClaims(String tenantId, String actor) {}

    /**
     * Um JWT autenticado sem claim de tenant tem de ser recusado aqui (E2E-021).
     *
     * <p>Antes o filtro seguia em frente sem ligar nada ao {@code TenantContextHolder}, e a
     * requisicao morria adiante com {@code relation ... does not exist}, porque o resolver cai em
     * {@code neuroassist_global}, onde as tabelas de negocio nao existem. Nao vazava dado — mas a
     * seguranca dependia de uma coincidencia, e o chamador recebia 500 em vez de saber o que
     * faltava. Era tambem o que tornava perigoso remover os aliases de claim.
     *
     * <p><b>Excecao deliberada:</b> {@code token_type=service} pode legitimamente nao ter tenant
     * (E2E-029 — o cliente que cria empresas nao tem empresa a que se escopar).
     */
    private boolean requiresTenantClaim() {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (!(current instanceof JwtAuthenticationToken jwtAuth)) {
            return false; // sem JWT: quem decide o acesso e o SecurityConfig
        }
        return !"service".equals(jwtAuth.getToken().getClaimAsString("token_type"));
    }

    /** 403 explicito, com um codigo que diz o que faltava. */
    private void writeMissingTenantClaim(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"tenant_claim_missing\"}");
    }

}
