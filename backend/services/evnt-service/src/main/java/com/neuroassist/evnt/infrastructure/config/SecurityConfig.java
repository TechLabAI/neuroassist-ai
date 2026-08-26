package com.neuroassist.evnt.infrastructure.config;

import com.neuroassist.evnt.infrastructure.security.TenantContextFilter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Resource-server configuration for evnt-service (closes C-17).
 *
 * <p>Until the Sessão 4 of the E2E plan this class was a PoC stub — {@code
 * anyRequest().permitAll()} with no {@code oauth2ResourceServer}. Two consequences, neither visible
 * to the module's own gates:
 *
 * <ol>
 *   <li>The bearer token was never decoded, so {@code TenantContextFilter} had no claims to read
 *       and hardcoded the tenant {@code "poc"} — the Postgres {@code search_path} resolved to a
 *       schema that does not exist, and the C-10 fix already applied to {@code
 *       MultiTenantConnectionProvider} stayed inert.
 *   <li>Every endpoint was anonymous, so the module's BDD scenarios could never exercise a 401 or a
 *       403 — exactly the hole found in gest-service before it was hardened.
 * </ol>
 *
 * <p><b>Authority model.</b> Three distinct callers reach this service, and each gets the identity
 * that matches what it is:
 *
 * <ul>
 *   <li>{@code POST /api/v1/reports} — the edge device agent, holding a device token minted by
 *       {@code POST /oauth2/token/device} with the {@code edge.device} authority (C-18). The tenant
 *       comes from the token's {@code tenant_id} claim, which is what makes the {@code
 *       tenant-mismatch} guard of EVNT-022 meaningful: the body can no longer assert which tenant
 *       the device belongs to.
 *   <li>{@code /api/v1/notifications/**} and {@code POST /api/v1/events/&#123;id&#125;/confirm} —
 *       dash-service, holding its own service-identity token ({@code SVC_DASH}). The caregiver
 *       travels as an explicit {@code caregiverId} parameter on every one of those calls; DASH
 *       already authorised the person at its own {@code /panel/**} boundary, so the authority of
 *       the call is the service (service-identity.md §4).
 *   <li>everything else — a caregiver JWT carrying {@code evnt.event-query.read} / {@code
 *       evnt.event-confirmation.confirmar}.
 * </ul>
 *
 * <p>Fine-grained enforcement lives in {@code @PreAuthorize} on each controller method; the chain
 * only guarantees that an unknown endpoint still requires a valid JWT.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
// Only in a servlet context. Several suites boot with webEnvironment = NONE, where Boot does not
// auto-configure a JwtDecoder; without this guard @EnableWebSecurity would still build the chain
// and fail the whole context on a missing bean that such a test never needed.
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, TenantContextFilter tenantContextFilter) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/actuator/health",
                                                "/actuator/health/**",
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
                // Must run AFTER BearerTokenAuthenticationFilter: the filter reads the tenant off
                // the decoded JWT, so the SecurityContext has to be populated first.
                .addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Reads the union of the {@code scope} claim and the {@code authorities} claim, both with an
     * empty prefix.
     *
     * <p>acs-service publishes fine-grained authorities in the {@code authorities} claim and never
     * populates {@code scope}. Spring's default converter reads only {@code scope}, so a service
     * that keeps the default sees zero authorities and answers 403 to every {@code @PreAuthorize} —
     * defect C-1 of the E2E plan, which cost an entire session to diagnose.
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
     * Declares the tenant filter explicitly rather than letting {@code @Component} scanning find it
     * — see the class javadoc of {@link TenantContextFilter}.
     */
    @Bean
    public TenantContextFilter tenantContextFilter() {
        return new TenantContextFilter();
    }

    /**
     * Cancels Spring Boot's automatic servlet-level registration of {@link TenantContextFilter}.
     * The filter must run only inside Spring Security's chain, after the JWT has been decoded.
     */
    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(
            TenantContextFilter filter) {
        FilterRegistrationBean<TenantContextFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
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
