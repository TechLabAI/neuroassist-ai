package com.neuroassist.gwy.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(
                        ex ->
                                ex.pathMatchers(
                                                "/actuator/health",
                                                "/actuator/prometheus",
                                                "/oauth2/jwks",
                                                "/oauth2/**",
                                                "/api/v1/*/actuator/health")
                                        .permitAll()
                                        .anyExchange()
                                        .authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> {}))
                .build();
    }
}
