package com.neuroassist.edge.infrastructure.config;

import com.neuroassist.edge.infrastructure.security.TenantContextFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.LinkedHashSet;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 JWT Resource Server security configuration (EDGE-012, P5).
 *
 * <p>Scope enforcement per endpoint via {@code @PreAuthorize} (enabled by {@link
 * EnableMethodSecurity}). Scopes: {@code edge.manage} (operators), {@code edge.device} (agents),
 * {@code edge.read} (DASH/PAC), {@code edge.internal} (service-to-service). POST /enroll is
 * permitAll — guarded by single-use enrollment token (api-contract §2.2, ADR-005).
 *
 * <p>Authority prefix: empty — so {@code hasAuthority("edge.manage")} matches the {@code scope}
 * claim value directly (no SCOPE_ prefix).
 *
 * <p>TenantContextFilter is added INSIDE the SecurityFilterChain, after
 * BearerTokenAuthenticationFilter, so JWT claims are available when tenant is extracted.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /**
     * Primary SecurityFilterChain. Stateless, CSRF disabled (all clients use Bearer token).
     * TenantContextFilter injected after JWT authentication to guarantee SecurityContext is
     * populated.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, TenantContextFilter tenantContextFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/actuator/health",
                                                "/actuator/info",
                                                "/actuator/metrics",
                                                "/actuator/metrics/**",
                                                "/actuator/prometheus")
                                        .permitAll()
                                        // POST /enroll: no Bearer — guarded by enrollment token
                                        // (api-contract §2.2)
                                        .requestMatchers(
                                                HttpMethod.POST, "/api/v1/edge/devices/enroll")
                                        .permitAll()
                                        // Scope enforcement at filter-chain level (defense-in-depth
                                        // alongside @PreAuthorize; filter-level enforcement is
                                        // reliable in all test contexts — @WebMvcTest,
                                        // @SpringBootTest, etc.).
                                        .requestMatchers(HttpMethod.POST, "/api/v1/edge/devices")
                                        .hasAuthority("edge.manage")
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/edge/devices/*/retire",
                                                "/api/v1/edge/devices/*/pairings",
                                                "/api/v1/edge/devices/*/pairings/current")
                                        .hasAuthority("edge.manage")
                                        .requestMatchers(
                                                HttpMethod.DELETE,
                                                "/api/v1/edge/devices/*/pairings/current")
                                        .hasAuthority("edge.manage")
                                        .requestMatchers(HttpMethod.POST, "/api/v1/edge/heartbeat")
                                        .hasAuthority("edge.device")
                                        // Superfície interna (C-18 / ADR-008): fora de
                                        // /api/v1/edge/** de propósito — o GWY não roteia
                                        // /internal/**, então só a malha de serviços alcança.
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/internal/devices/verify-credential")
                                        .hasAuthority("edge.internal")
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/edge/devices/*/liveness",
                                                "/api/v1/edge/patients/*/liveness",
                                                "/api/v1/edge/patients/*/device")
                                        .hasAnyAuthority("edge.read", "edge.internal")
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        ex ->
                                ex.accessDeniedHandler(
                                        (request, response, denied) -> {
                                            // Filter-chain authorization denial (hasAuthority
                                            // check).
                                            // The default handler returns 403 with no body.
                                            // This handler writes a ProblemDetail JSON so FE and
                                            // BDD
                                            // assertions on the error type are not vacuous.
                                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                            response.setContentType(
                                                    "application/problem+json;charset=UTF-8");
                                            response.getWriter()
                                                    .write(
                                                            "{"
                                                                    + "\"type\":\"https://neuroassist-ai.com/errors/edge.insufficient-scope\","
                                                                    + "\"status\":403,"
                                                                    + "\"title\":\"Insufficient scope for this operation\","
                                                                    + "\"detail\":\"The token does not carry the required authority\""
                                                                    + "}");
                                        }))
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter())))
                // TenantContextFilter runs AFTER BearerTokenAuthenticationFilter so that
                // SecurityContext (and JWT claims) are already populated when tenant is extracted.
                .addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * JWT authorities converter with empty prefix so scope claim values map directly to authority
     * names. This enables {@code @PreAuthorize("hasAuthority('edge.manage')")} to match the scope
     * {@code edge.manage} in the JWT without the {@code SCOPE_} prefix added by default.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Empty prefix: scope "edge.manage" → authority "edge.manage" (not "SCOPE_edge.manage")
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        scopeConverter.setAuthorityPrefix("");

        // acs-service publishes authorities in the "authorities" claim, never in "scope" (E2E-003)
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("");
        authoritiesConverter.setAuthoritiesClaimName("authorities");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> {
                    Collection<GrantedAuthority> merged = new LinkedHashSet<>();
                    merged.addAll(scopeConverter.convert(jwt));
                    merged.addAll(authoritiesConverter.convert(jwt));
                    return merged;
                });
        return converter;
    }

    /**
     * Prevent Spring Boot from auto-registering TenantContextFilter as a standalone Servlet filter.
     * It is added INSIDE the SecurityFilterChain via addFilterAfter(), so it must not be registered
     * again at the Servlet container level.
     */
    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(
            TenantContextFilter filter) {
        FilterRegistrationBean<TenantContextFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    /** TenantContextFilter bean — managed here to control its lifecycle and registration. */
    @Bean
    public TenantContextFilter tenantContextFilter() {
        return new TenantContextFilter();
    }
}
