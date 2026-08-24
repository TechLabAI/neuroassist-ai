package com.neuroassist.gest.infrastructure.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * OAuth2 JWT Resource Server security configuration.
 *
 * <p>gest-service is a real resource server: {@code @EnableMethodSecurity} activates the
 * {@code @PreAuthorize} annotations on the controllers and the chain ends in {@code
 * anyRequest().authenticated()} — only the actuator probes stay public. Before this, the chain
 * ended in {@code anyRequest().permitAll()} and method security was off, so every
 * {@code @PreAuthorize} was inert and PAC → GEST / EDGE → GEST calls succeeded through the
 * *absence* of authorization rather than through correct authorization.
 *
 * <p>Two authentication mechanisms coexist on the same chain:
 *
 * <ul>
 *   <li><b>Bearer JWT</b> (production path) — tokens minted by acs-service; authorities are read
 *       from the union of the {@code scope} and {@code authorities} claims with an empty prefix, so
 *       {@code hasAuthority('gest.tipo-gesto.read')} matches the claim value verbatim (E2E-003 /
 *       C-1). Decoding also feeds {@code TenantContextFilter}, which reads the {@code tenant} claim
 *       (E2E-009 / C-14).
 *   <li><b>HTTP Basic</b> with in-memory users — kept because the RBAC BDD scenarios ({@code
 *       tipo-gesto-lifecycle.feature}, via {@code TipoGestoLifecycleSteps}) authenticate as {@code
 *       gest-manager} / {@code gest-reader}. Their authority sets mirror the acs-service {@code
 *       RoleAuthorityPolicy} entries for COORDENADOR_CLINICA and CUIDADORA respectively.
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/actuator/**")
                                        .permitAll()
                                        // Filter-level enforcement mirrors the @PreAuthorize on the
                                        // controller (defense in depth; reliable in every test
                                        // context, including @WebMvcTest slices).
                                        .requestMatchers(
                                                HttpMethod.POST, "/gest-service/tipo-gesto")
                                        .hasAuthority("gest.tipo-gesto.criar")
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/gest-service/tipo-gesto/*/transitions")
                                        .hasAuthority("gest.tipo-gesto.editar")
                                        .anyRequest()
                                        .authenticated())
                .httpBasic(Customizer.withDefaults())
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter())))
                .exceptionHandling(
                        ex ->
                                ex.accessDeniedHandler(
                                        (req, res, e) -> {
                                            res.setStatus(403);
                                            res.setContentType("application/problem+json");
                                            res.getWriter()
                                                    .write(
                                                            "{\"type\":\"/errors/insufficient-scope\","
                                                                    + "\"title\":\"Forbidden\","
                                                                    + "\"status\":403,"
                                                                    + "\"detail\":\"Insufficient scope for this operation\"}");
                                        }))
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * Maps JWT claims to authorities, reading the union of the standard {@code scope} claim and the
     * {@code authorities} claim that acs-service actually populates (E2E-003 / C-1).
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        scopeConverter.setAuthorityPrefix("");

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
     * In-memory users used only by the RBAC BDD scenarios (HTTP Basic). The authority names are the
     * canonical ones enforced by the controllers' {@code @PreAuthorize} and emitted by acs-service
     * {@code RoleAuthorityPolicy} — the previous {@code gest.profile.*} / {@code
     * gest.contract.read} names matched nothing and would silently 403 now that method security is
     * active.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        // Mirrors CuidadorPapel.COORDENADOR_CLINICA
        UserDetails manager =
                User.withUsername("gest-manager")
                        .password("{noop}manager-secret")
                        .authorities(
                                "gest.tipo-gesto.read",
                                "gest.tipo-gesto.criar",
                                "gest.tipo-gesto.editar",
                                "gest.detection-profile.read",
                                "gest.detection-profile.publicar",
                                "gest.detection-contract.read",
                                "gest.detection-contract.validar",
                                "gest.eligibility.read")
                        .build();
        // Mirrors CuidadorPapel.CUIDADORA — read-only for tipo-gesto, but can validate reports
        UserDetails reader =
                User.withUsername("gest-reader")
                        .password("{noop}reader-secret")
                        .authorities(
                                "gest.tipo-gesto.read",
                                "gest.detection-profile.read",
                                "gest.detection-contract.read",
                                "gest.detection-contract.validar",
                                "gest.eligibility.read")
                        .build();
        return new InMemoryUserDetailsManager(manager, reader);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
