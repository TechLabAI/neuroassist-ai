package com.neuroassist.gest.infrastructure.config;

import com.neuroassist.gest.infrastructure.security.TenantContextFilter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * P5 (GEST-017): OAuth2 Resource Server — stateless JWT validation, method-level security, and
 * tenant context wiring inside the filter chain.
 *
 * <p>Key design decisions: — {@link TenantContextFilter} is registered via {@code addFilterAfter}
 * (after {@link BearerTokenAuthenticationFilter}) so the JWT is already parsed before tenant
 * extraction runs. — {@link FilterRegistrationBean} with {@code setEnabled(false)} prevents Spring
 * Boot from auto-registering TenantContextFilter as a standalone servlet filter (which would cause
 * double execution and run it before Security). — {@link JwtGrantedAuthoritiesConverter} with empty
 * authority prefix maps JWT "scope" values directly to {@code GrantedAuthority} objects that match
 * the {@code hasAuthority('gest.tipo-gesto.read')} checks on controllers.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TenantContextFilter tenantContextFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/actuator/health",
                                                "/actuator/info",
                                                "/actuator/prometheus")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt ->
                                                jwt.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter())))
                .addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Disables Spring Boot's automatic registration of {@link TenantContextFilter} as a standalone
     * servlet filter. The filter is instead added inside the Security filter chain via {@code
     * addFilterAfter} in {@link #securityFilterChain}.
     */
    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration() {
        FilterRegistrationBean<TenantContextFilter> registration =
                new FilterRegistrationBean<>(tenantContextFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Maps JWT "scope" claim values directly to {@link
     * org.springframework.security.core.GrantedAuthority} objects — no {@code SCOPE_} prefix — so
     * that controller-level {@code @PreAuthorize} checks such as {@code
     * hasAuthority('gest.tipo-gesto.read')} work without prefix manipulation.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix(""); // strip the default "SCOPE_" prefix
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
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
