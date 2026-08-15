package com.neuroassist.gest.infrastructure.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * PoC security: HTTP Basic with in-memory users. Write endpoints on /gest-service/tipo-gesto
 * require the gest.tipo-gesto.criar / editar authority; all other requests are permitted. This
 * replaces the earlier permitAll stub to allow RBAC BDD scenarios to pass.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.POST, "/gest-service/tipo-gesto")
                                        .hasAuthority("gest.tipo-gesto.criar")
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/gest-service/tipo-gesto/*/transitions")
                                        .hasAuthority("gest.tipo-gesto.editar")
                                        .anyRequest()
                                        .permitAll())
                .httpBasic(Customizer.withDefaults())
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

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails manager =
                User.withUsername("gest-manager")
                        .password("{noop}manager-secret")
                        .authorities(
                                "gest.tipo-gesto.read",
                                "gest.tipo-gesto.criar",
                                "gest.tipo-gesto.editar",
                                "gest.profile.read",
                                "gest.profile.criar",
                                "gest.eligibility.read",
                                "gest.contract.read")
                        .build();
        UserDetails reader =
                User.withUsername("gest-reader")
                        .password("{noop}reader-secret")
                        .authorities("gest.tipo-gesto.read", "gest.profile.read")
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
