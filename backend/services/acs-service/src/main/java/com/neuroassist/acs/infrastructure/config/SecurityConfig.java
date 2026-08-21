package com.neuroassist.acs.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.acs.shared.error.ProblemDetailFactory;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for acs-service (P2 baseline — ACS-021).
 *
 * <p>JWT Resource Server: validates tokens issued by acs-service itself using the transient RSA key
 * pair configured in JwtEncoderConfig (P2) or DB-backed key (P3+).
 *
 * <p>Matchers:
 *
 * <ul>
 *   <li>/oauth2/token, /oauth2/token/refresh — public (login + refresh)
 *   <li>/.well-known/jwks.json — public (JWKS for other services)
 *   <li>/actuator/health, /actuator/prometheus — public (infrastructure)
 *   <li>/api/v1/acs/tenants/** — requires acs.tenant.* authority
 *   <li>/api/v1/acs/** — authenticated (fine-grained via @PreAuthorize)
 * </ul>
 *
 * <p>CSRF disabled (stateless JWT-based API, no cookie session). RULE-08:
 * spring.jpa.open-in-view=false enforced in application.yml.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            ObjectMapper objectMapper)
            throws Exception {

        // Custom entry point writes RFC 7807 JSON body for filter-level 401 rejections.
        // Spring's default BearerTokenAuthenticationEntryPoint returns an empty body, which
        // would fail assertErrorTypeContains("unauthenticated") in the BDD suite.
        AuthenticationEntryPoint unauthenticatedEntryPoint =
                (request, response, authException) -> {
                    var pd =
                            ProblemDetailFactory.forCode(
                                    "acs.auth-unauthenticated", authException, null, Map.of());
                    pd.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    objectMapper.writeValue(response.getWriter(), pd);
                };

        http
                // Stateless — no session needed for JWT-based auth
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF disabled — stateless JWT API
                .csrf(csrf -> csrf.disable())

                // Authorization rules
                .authorizeHttpRequests(
                        auth ->
                                auth
                                        // Public endpoints (no auth required)
                                        .requestMatchers(
                                                "/oauth2/token",
                                                "/oauth2/token/refresh",
                                                // Service identity: authenticated inside the
                                                // service against the configured BCrypt hash
                                                "/oauth2/token/service",
                                                "/.well-known/jwks.json",
                                                "/oauth2/jwks",
                                                "/actuator/health",
                                                "/actuator/prometheus")
                                        .permitAll()
                                        // All other requests require a valid JWT
                                        .anyRequest()
                                        .authenticated())

                // Wire custom entry point for 401 at both the security exception handler level
                // and inside the resource server (covers both missing and invalid tokens)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthenticatedEntryPoint))

                // JWT Resource Server — validates tokens using local RSA public key
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.authenticationEntryPoint(unauthenticatedEntryPoint)
                                        .jwt(
                                                jwt ->
                                                        jwt.decoder(jwtDecoder)
                                                                .jwtAuthenticationConverter(
                                                                        jwtAuthenticationConverter)));

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter =
                new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("authorities");
        grantedAuthoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }
}
