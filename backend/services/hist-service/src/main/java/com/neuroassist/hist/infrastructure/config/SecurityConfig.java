package com.neuroassist.hist.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for hist-service (HIST-014, P5).
 *
 * <p>OAuth2 Resource Server — JWK Set URI is resolved from: {@code
 * spring.security.oauth2.resourceserver.jwt.jwk-set-uri} (defined in application.yml /
 * application-test.yml). Scope rules:
 *
 * <ul>
 *   <li>POST /api/v1/hist/audit-facts → SCOPE_hist:write
 *   <li>DELETE /api/v1/hist/patients/{id}/personal-data → SCOPE_hist:erase
 *   <li>GET /api/v1/hist/history/verify → SCOPE_hist:read:all
 *   <li>GET /api/v1/hist/history/** → SCOPE_hist:read OR SCOPE_hist:read:all
 *   <li>GET /api/v1/hist/audit-records/** → SCOPE_hist:read OR SCOPE_hist:read:all
 *   <li>GET /api/v1/hist/audit-facts/** → SCOPE_hist:read OR SCOPE_hist:read:all
 *   <li>GET /actuator/** → public (health checks)
 *   <li>any other → authenticated (401 if no token)
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String TYPE_BASE = "https://neuroassist-ai.com/errors/";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        ObjectMapper problemMapper = new ObjectMapper();

        http.csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                                (request, response, authException) -> {
                                                    // 401 Unauthorized — return ProblemDetail body
                                                    ProblemDetail pd =
                                                            ProblemDetail.forStatus(
                                                                    HttpStatus.UNAUTHORIZED);
                                                    pd.setTitle("Unauthorized");
                                                    pd.setDetail(
                                                            "Authentication required or token invalid.");
                                                    pd.setType(
                                                            URI.create(TYPE_BASE + "unauthorized"));
                                                    pd.setInstance(
                                                            URI.create(request.getRequestURI()));
                                                    pd.setProperty(
                                                            "error",
                                                            Map.of(
                                                                    "code",
                                                                    "unauthorized",
                                                                    "category",
                                                                    "security",
                                                                    "timestamp",
                                                                    OffsetDateTime.now()
                                                                            .toString()));
                                                    response.setStatus(
                                                            HttpStatus.UNAUTHORIZED.value());
                                                    response.setContentType(
                                                            MediaType.APPLICATION_JSON_VALUE);
                                                    response.getWriter()
                                                            .write(
                                                                    problemMapper
                                                                            .writeValueAsString(
                                                                                    pd));
                                                })
                                        .accessDeniedHandler(
                                                (request, response, accessDeniedException) -> {
                                                    // 403 Forbidden — return ProblemDetail body
                                                    ProblemDetail pd =
                                                            ProblemDetail.forStatus(
                                                                    HttpStatus.FORBIDDEN);
                                                    pd.setTitle("Forbidden");
                                                    pd.setDetail(
                                                            "Insufficient scope for this operation.");
                                                    pd.setType(URI.create(TYPE_BASE + "forbidden"));
                                                    pd.setInstance(
                                                            URI.create(request.getRequestURI()));
                                                    pd.setProperty(
                                                            "error",
                                                            Map.of(
                                                                    "code",
                                                                    "forbidden",
                                                                    "category",
                                                                    "security",
                                                                    "timestamp",
                                                                    OffsetDateTime.now()
                                                                            .toString()));
                                                    response.setStatus(
                                                            HttpStatus.FORBIDDEN.value());
                                                    response.setContentType(
                                                            MediaType.APPLICATION_JSON_VALUE);
                                                    response.getWriter()
                                                            .write(
                                                                    problemMapper
                                                                            .writeValueAsString(
                                                                                    pd));
                                                }))
                .authorizeHttpRequests(
                        auth ->
                                auth
                                        // Health / metrics — no token needed
                                        .requestMatchers(HttpMethod.GET, "/actuator/**")
                                        .permitAll()

                                        // Write — requires hist:write
                                        .requestMatchers(
                                                HttpMethod.POST, "/api/v1/hist/audit-facts")
                                        .hasAuthority("SCOPE_hist:write")

                                        // LGPD erasure — requires hist:erase, an authority
                                        // distinct from hist:write on purpose: appending a fact
                                        // and destroying a patient's key are different powers.
                                        // Declared here rather than relying on @PreAuthorize
                                        // alone, which was the only guard on this path.
                                        .requestMatchers(
                                                HttpMethod.DELETE,
                                                "/api/v1/hist/patients/*/personal-data")
                                        .hasAuthority("SCOPE_hist:erase")

                                        // Chain verify — requires hist:read:all (more restrictive;
                                        // must be declared before the generic history/** rule)
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/hist/history/verify")
                                        .hasAuthority("SCOPE_hist:read:all")

                                        // History timeline — requires hist:read or hist:read:all
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/hist/history",
                                                "/api/v1/hist/history/**")
                                        .hasAnyAuthority("SCOPE_hist:read", "SCOPE_hist:read:all")

                                        // Audit record read by id
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/hist/audit-records/**")
                                        .hasAnyAuthority("SCOPE_hist:read", "SCOPE_hist:read:all")

                                        // Audit fact read by id (GET on write path)
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/hist/audit-facts/**")
                                        .hasAnyAuthority("SCOPE_hist:read", "SCOPE_hist:read:all")

                                        // Everything else — valid token required (401 if absent)
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt ->
                                                // JWK URI configured via application.yml
                                                // spring.security.oauth2.resourceserver.jwt.jwk-set-uri
                                                jwt.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Maps JWT claims to authorities using the {@code SCOPE_} prefix expected by the rules above.
     *
     * <p>Two claims are read and unioned:
     *
     * <ul>
     *   <li>{@code scope}/{@code scp} — the standard OAuth2 claim (used by the BDD suite)
     *   <li>{@code authorities} — the claim acs-service actually populates ({@code TokenIssuer})
     * </ul>
     *
     * <p>Without the {@code authorities} claim, every real ACS token would yield zero authorities
     * and each rule above would answer 403 (E2E-003).
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
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
}
