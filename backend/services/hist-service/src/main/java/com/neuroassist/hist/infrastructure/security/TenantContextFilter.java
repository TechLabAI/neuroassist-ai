package com.neuroassist.hist.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads tenantId from JWT claim ("tenant_id") and binds it to TenantContextHolder for the duration
 * of the request. Validates X-Tenant-Id header (if present) matches.
 *
 * <p>Runs AFTER Spring Security's DelegatingFilterProxy (order -100) so that the JWT has already
 * been validated and placed in SecurityContextHolder before this filter reads the tenant claim (P5,
 * HIST-015). {@code LOWEST_PRECEDENCE - 10} ensures it is among the last filters in the chain.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String JWT_CLAIM_TENANT_ID = "tenant_id";
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String MDC_KEY_TENANT = "tenant";

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
            MDC.put(MDC_KEY_TENANT, tenantId);
        } else if (requiresTenantClaim()) {
            writeMissingTenantClaim(response);
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            MDC.remove(MDC_KEY_TENANT);
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

    /**
     * Um JWT autenticado sem claim de tenant tem de ser recusado aqui (E2E-021).
     *
     * <p>Antes o filtro seguia em frente: nada era ligado ao {@code TenantContextHolder}, e a
     * requisicao morria adiante com {@code relation ... does not exist}, porque o resolver cai em
     * {@code neuroassist_global}, onde as tabelas de negocio nao existem. Nao vazava dado — mas a
     * seguranca dependia de uma coincidencia (o schema de fallback nao conter tabela de negocio), e
     * o chamador recebia 500 em vez de saber o que faltava. Era tambem o que tornava perigoso
     * remover os aliases de claim: quatro servicos liam so um alias, e parar de emiti-lo os faria
     * resolver tenant nulo em silencio.
     *
     * <p><b>Excecao deliberada:</b> {@code token_type=service} pode legitimamente nao ter tenant —
     * o cliente que cria empresas nao tem empresa a que se escopar (E2E-029), e o ACS so concede
     * isso a clientes marcados como tenant-less.
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
