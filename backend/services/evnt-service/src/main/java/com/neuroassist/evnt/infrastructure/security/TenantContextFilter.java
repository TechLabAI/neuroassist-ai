package com.neuroassist.evnt.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds the tenant of the authenticated principal to {@link TenantContextHolder} for the duration
 * of the request (RULE-07).
 *
 * <p>This class used to be a PoC stub that did {@code TenantContextHolder.set("poc")} on every
 * request (C-17). Because {@code SecurityConfig} never enabled {@code oauth2ResourceServer}, there
 * were no claims to read anyway — and the literal {@code "poc"} then flowed into {@code
 * MultiTenantConnectionProvider}, which set the Postgres {@code search_path} to a schema that does
 * not exist. Every HTTP-driven write in this service was addressed to nowhere.
 *
 * <p><b>Claim order.</b> {@code tenant_id} is the canonical claim; {@code tenant} and {@code
 * empresaId} are compatibility aliases acs-service still emits with the same value (C-3). Reading
 * all three in order means this filter keeps working when the aliases are finally dropped.
 *
 * <p>Registered inside Spring Security's chain after {@code BearerTokenAuthenticationFilter} — see
 * {@code SecurityConfig#securityFilterChain}. It deliberately does <b>not</b> reject a request
 * whose token carries no tenant: service-identity tokens for in-process work and the actuator
 * probes legitimately have none, and every method that touches tenant data already calls {@code
 * TenantContextHolder.require()}, which fails loudly.
 *
 * <p><b>Not a {@code @Component} on purpose.</b> Spring Boot auto-registers any {@code Filter} bean
 * it finds into the servlet chain, which runs <i>before</i> Spring Security. Being a {@code
 * OncePerRequestFilter}, that earlier pass — where no JWT has been decoded yet — would mark the
 * request as already filtered and the in-chain instance would never run. It is declared explicitly
 * by {@code SecurityConfig}, which also disables the servlet-level registration.
 */
@Slf4j
public class TenantContextFilter extends OncePerRequestFilter {

    /** Canonical claim first, then the compatibility aliases acs-service still emits (C-3). */
    private static final List<String> TENANT_CLAIMS = List.of("tenant_id", "tenant", "empresaId");

    private static final String MDC_ACTOR = "userId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Jwt jwt = currentJwt();
        String tenantId = jwt == null ? null : firstNonBlankClaim(jwt);
        String actor = jwt == null ? null : asString(jwt.getClaim("sub"));

        if (tenantId != null) {
            TenantContextHolder.set(tenantId);
        } else if (jwt != null) {
            log.debug(
                    "Authenticated request without a tenant claim path={} sub={}",
                    request.getRequestURI(),
                    actor);
        }
        if (actor != null) {
            MDC.put(MDC_ACTOR, actor);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            MDC.remove(MDC_ACTOR);
        }
    }

    private Jwt currentJwt() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwtAuth ? jwtAuth.getToken() : null;
    }

    private String firstNonBlankClaim(Jwt jwt) {
        for (String claim : TENANT_CLAIMS) {
            String value = asString(jwt.getClaim(claim));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String asString(Object claim) {
        if (claim == null) {
            return null;
        }
        String value = claim.toString();
        return value.isBlank() ? null : value;
    }
}
