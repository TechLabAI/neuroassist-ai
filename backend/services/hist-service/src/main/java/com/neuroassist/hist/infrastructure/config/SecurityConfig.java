package com.neuroassist.hist.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for hist-service (HIST-014, P5).
 *
 * <p>OAuth2 Resource Server — JWK Set URI is resolved from:
 * {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri} (defined in application.yml /
 * application-test.yml). Scope rules:
 *
 * <ul>
 *   <li>POST /api/v1/hist/audit-facts → SCOPE_hist:write
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
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

                                        // Chain verify — requires hist:read:all (more restrictive;
                                        // must be declared before the generic history/** rule)
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/hist/history/verify")
                                        .hasAuthority("SCOPE_hist:read:all")

                                        // History timeline — requires hist:read or hist:read:all
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/v1/hist/history",
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
                                        jwt -> {
                                            // JWK URI configured via application.yml
                                            // spring.security.oauth2.resourceserver.jwt.jwk-set-uri
                                        }));

        return http.build();
    }
}
