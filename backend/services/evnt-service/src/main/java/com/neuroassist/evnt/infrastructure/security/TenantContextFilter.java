package com.neuroassist.evnt.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
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
    /**
     * Um nome so (E2E-021). Este filtro lia a uniao dos tres — {@code tenant_id}, {@code tenant} e
     * {@code empresaId} —, o que o tornava imune ao problema mas mantinha os aliases vivos: eram
     * lidos em algum lugar, logo nao podiam ser removidos.
     */
    private static final List<String> TENANT_CLAIMS = List.of("tenant_id");

    private static final String HEADER_TENANT_ID = "X-Tenant-Id";
    private static final String MDC_ACTOR = "userId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Jwt jwt = currentJwt();
        String tenantId = jwt == null ? null : firstNonBlankClaim(jwt);
        String actor = jwt == null ? null : asString(jwt.getClaim("sub"));

        if (tenantId != null) {
            // Confere o header contra o claim, como os outros seis servicos (E2E-021). O EVNT era
            // o unico que nao conferia: um X-Tenant-Id divergente do JWT era simplesmente ignorado,
            // e a requisicao respondia igual. Nao havia vazamento — o tenant sempre veio do claim
            // assinado, nunca do header —, mas a politica precisa ser a mesma nos sete: um header
            // divergente e um chamador confuso ou uma tentativa, e as duas merecem resposta, nao
            // silencio.
            String headerTenant = request.getHeader(HEADER_TENANT_ID);
            if (headerTenant != null && !headerTenant.isBlank() && !headerTenant.equals(tenantId)) {
                log.warn(
                        "Tenant mismatch: jwt.tenant_id={} header.X-Tenant-Id={} path={}",
                        tenantId,
                        headerTenant,
                        request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"tenant_mismatch\"}");
                return;
            }
            TenantContextHolder.set(tenantId);
        } else if (jwt != null && requiresTenantClaim()) {
            // Era um log.debug. Um debug e exatamente o que ninguem le quando a requisicao morre
            // 200ms depois com "relation ... does not exist" (E2E-021).
            log.warn(
                    "Requisicao autenticada sem claim de tenant path={} sub={} — recusada",
                    request.getRequestURI(),
                    actor);
            writeMissingTenantClaim(response);
            return;
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
