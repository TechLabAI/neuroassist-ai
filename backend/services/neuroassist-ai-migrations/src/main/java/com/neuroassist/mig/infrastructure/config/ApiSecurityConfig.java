package com.neuroassist.mig.infrastructure.config;

import java.util.ArrayList;
import java.util.Collection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security for MIG's internal HTTP face (E2E-016, api-contract §1.4).
 *
 * <p>Only active under the {@code api} profile — the deploy-time job has no web layer to secure.
 *
 * <p><b>The converter reads the union of {@code scope} and {@code authorities}.</b> acs-service
 * puts fine-grained authorities in the {@code authorities} claim, and Spring's default converter
 * reads only {@code scope}. A resource server wired with the default gets zero authorities and
 * answers 403 to every {@code @PreAuthorize} — the failure catalogued in CLAUDE.md under "Auth
 * backbone". Empty prefix on both, matching every service except hist-service.
 */
@Configuration
@Profile("api")
@EnableMethodSecurity
public class ApiSecurityConfig {

    @Bean
    public SecurityFilterChain internalApiFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.GET, "/actuator/health/**")
                                        .permitAll()
                                        // Everything else is service-to-service. There is no
                                        // anonymous surface here on purpose: this service holds the
                                        // DDL role for every schema in the platform.
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter())));
        return http.build();
    }

    private Converter<Jwt, AbstractAuthenticationToken> converter() {
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        scopeConverter.setAuthorityPrefix("");

        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("");
        authoritiesConverter.setAuthoritiesClaimName("authorities");

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(
                jwt -> {
                    Collection<GrantedAuthority> union = new ArrayList<>();
                    union.addAll(scopeConverter.convert(jwt));
                    union.addAll(authoritiesConverter.convert(jwt));
                    return union;
                });
        return jwtConverter;
    }
}
