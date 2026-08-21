package com.neuroassist.dash.infrastructure.config;

import com.neuroassist.dash.infrastructure.security.CorrelationIdFilter;
import com.neuroassist.dash.infrastructure.security.MdcUserContextFilter;
import com.neuroassist.dash.infrastructure.security.TenantContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Seguranca DASH: duas superficies de autenticacao.
 *
 * <ul>
 *   <li>/panel/** — JWT cuidador (ROLE_CAREGIVER) — roteado pelo Gateway
 *   <li>/internal/** — JWT servico (SVC_EVNT) — chamado direto pelo EVNT service
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CorrelationIdFilter correlationIdFilter;
    private final MdcUserContextFilter mdcUserContextFilter;
    private final TenantContextFilter tenantContextFilter;

    public SecurityConfig(
            CorrelationIdFilter correlationIdFilter,
            MdcUserContextFilter mdcUserContextFilter,
            TenantContextFilter tenantContextFilter) {
        this.correlationIdFilter = correlationIdFilter;
        this.mdcUserContextFilter = mdcUserContextFilter;
        this.tenantContextFilter = tenantContextFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/actuator/health",
                                                "/actuator/prometheus",
                                                "/actuator/sessions")
                                        .permitAll()
                                        .requestMatchers("/internal/**")
                                        .hasAuthority("SVC_EVNT")
                                        .requestMatchers("/panel/**")
                                        .hasAuthority("ROLE_CAREGIVER")
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2.jwt(
                                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())))
                // Correlation ID is earliest: even 401 responses carry X-Correlation-Id
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                // Tenant context: after JWT auth is resolved
                .addFilterBefore(tenantContextFilter, UsernamePasswordAuthenticationFilter.class)
                // MDC userId: after Bearer token filter validates the JWT
                .addFilterAfter(mdcUserContextFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("authorities");
        authoritiesConverter.setAuthorityPrefix(""); // sem prefixo SCOPE_

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return jwtConverter;
    }
}
