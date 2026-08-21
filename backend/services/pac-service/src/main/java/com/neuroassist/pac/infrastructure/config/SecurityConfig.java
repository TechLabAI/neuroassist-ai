package com.neuroassist.pac.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.pac.infrastructure.security.TenantContextFilter;
import com.neuroassist.pac.infrastructure.security.TenantJwtValidator;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * OAuth2 JWT Resource Server security configuration (PAC-021, P7 — SPEC-06).
 *
 * <p>Scope enforcement per endpoint via {@code @PreAuthorize} (enabled by {@link
 * EnableMethodSecurity}). Scope names follow the PAC authority convention (no {@code SCOPE_}
 * prefix):
 *
 * <ul>
 *   <li>{@code pac.paciente.criar} — POST /pacientes
 *   <li>{@code pac.paciente.read} — GET /pacientes, GET /pacientes/{id}
 *   <li>{@code pac.paciente.editar} — PUT /pacientes/{id}
 *   <li>{@code pac.comando-authoring.criar} — POST /pacientes/{pid}/comandos
 *   <li>{@code pac.comando-authoring.editar} — PUT /pacientes/{pid}/comandos/{id}
 *   <li>{@code pac.comando-lifecycle.read} — GET /pacientes/{pid}/comandos/{id}
 *   <li>{@code pac.comando-lifecycle.publicar} — POST /{id}:publish
 *   <li>{@code pac.comando-lifecycle.aposentar} — POST /{id}:retire
 *   <li>{@code pac.comando-lifecycle.recalibrar} — POST /{id}:recalibrate
 *   <li>{@code pac.catalogo-ativo.read} — GET /pacientes/{pid}/catalogo-ativo
 * </ul>
 *
 * <p>{@link TenantContextFilter} is added INSIDE the SecurityFilterChain, after {@code
 * BearerTokenAuthenticationFilter}, so JWT claims are available when empresaId is extracted.
 *
 * <p>All 401/403 responses from the filter chain are RFC 7807 ProblemDetail with {@code
 * Content-Type: application/problem+json}.
 *
 * <p>RULE-05: no {@code api.*} imports. RULE-06 analogue: SecurityConfig stays in {@code
 * infrastructure.config}.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    /**
     * Primary SecurityFilterChain. Stateless (Bearer only), CSRF disabled.
     *
     * <p>Fine-grained scope enforcement is delegated to {@code @PreAuthorize} on each controller
     * method. The filter chain only enforces authentication ({@code anyRequest().authenticated()})
     * so that unknown endpoints also require a valid JWT.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, TenantContextFilter tenantContextFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
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
                                                                jwtAuthenticationConverter()))
                                        .authenticationEntryPoint(authenticationEntryPoint())
                                        .accessDeniedHandler(accessDeniedHandler()))
                // TenantContextFilter runs AFTER BearerTokenAuthenticationFilter so that
                // SecurityContext (JWT claims) are already populated when empresaId is extracted.
                .addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * JWT authorities converter with empty prefix so scope claim values map directly to authority
     * names. This enables {@code @PreAuthorize("hasAuthority('pac.paciente.criar')")} to match the
     * scope {@code pac.paciente.criar} in the JWT without a {@code SCOPE_} prefix.
     *
     * <p>Two claims are read and unioned: the standard {@code scope}/{@code scp} claim and the
     * {@code authorities} claim published by acs-service ({@code TokenIssuer}). Without the {@code
     * authorities} claim every {@code @PreAuthorize} would fail with 403 against a real ACS token,
     * since ACS never populates {@code scope} (E2E-003).
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Empty prefix: scope "pac.paciente.criar" → authority "pac.paciente.criar"
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
     * TenantContextFilter bean — managed here to control lifecycle and registration. NOT annotated
     * with {@code @Component} to avoid auto-detection by Spring Boot.
     */
    @Bean
    public TenantContextFilter tenantContextFilter(
            TenantJwtValidator tenantJwtValidator, ObjectMapper mapper) {
        return new TenantContextFilter(tenantJwtValidator, mapper);
    }

    /**
     * Prevent Spring Boot from auto-registering TenantContextFilter as a standalone Servlet filter.
     * It is added INSIDE the SecurityFilterChain via addFilterAfter(), so registering it again at
     * the Servlet container level would cause double execution.
     */
    @Bean
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(
            TenantContextFilter filter) {
        FilterRegistrationBean<TenantContextFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    // -----------------------------------------------------------------------
    // RFC 7807 handlers for filter-chain security rejections
    // -----------------------------------------------------------------------

    /**
     * AuthenticationEntryPoint: handles 401 when no valid JWT is present (missing, expired, or
     * malformed Bearer token).
     */
    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            log.warn(
                    "Unauthorized request path={} cause={}",
                    request.getRequestURI(),
                    authException.getMessage());
            String correlationId = resolveCorrelationId(request);
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("X-Correlation-Id", correlationId);
            Map<String, Object> body =
                    Map.of(
                            "type",
                            "/errors/auth.unauthorized",
                            "title",
                            "Unauthorized",
                            "status",
                            HttpStatus.UNAUTHORIZED.value(),
                            "detail",
                            "Authentication required — provide a valid Bearer token",
                            "instance",
                            request.getRequestURI(),
                            "error",
                            Map.of(
                                    "code",
                                    "auth.unauthorized",
                                    "category",
                                    "auth",
                                    "correlationId",
                                    correlationId,
                                    "timestamp",
                                    OffsetDateTime.now().toString(),
                                    "context",
                                    Map.of()));
            response.getWriter().write(objectMapper.writeValueAsString(body));
        };
    }

    /**
     * AccessDeniedHandler: handles 403 at the filter-chain level (authenticated user without the
     * required scope). {@code @PreAuthorize} failures in controllers are handled by {@code
     * GlobalExceptionHandler.handleAccessDenied}.
     */
    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            log.warn(
                    "Access denied (filter-chain) path={} cause={}",
                    request.getRequestURI(),
                    accessDeniedException.getMessage());
            String correlationId = resolveCorrelationId(request);
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("X-Correlation-Id", correlationId);
            Map<String, Object> body =
                    Map.of(
                            "type",
                            "/errors/insufficient-scope",
                            "title",
                            "Insufficient Scope",
                            "status",
                            HttpStatus.FORBIDDEN.value(),
                            "detail",
                            "Insufficient scope to access this resource",
                            "instance",
                            request.getRequestURI(),
                            "error",
                            Map.of(
                                    "code",
                                    "pac.insufficient-scope",
                                    "category",
                                    "forbidden",
                                    "correlationId",
                                    correlationId,
                                    "timestamp",
                                    OffsetDateTime.now().toString(),
                                    "context",
                                    Map.of()));
            response.getWriter().write(objectMapper.writeValueAsString(body));
        };
    }

    /**
     * Resolves correlationId: MDC (set by CorrelationIdInterceptor when available) → request header
     * → generated UUID. CorrelationIdInterceptor runs inside DispatcherServlet (MVC), which is
     * after the security filter chain; for filter-level rejections (401/403) the MVC interceptor
     * has not run yet, so the header or a generated UUID is used.
     */
    private String resolveCorrelationId(jakarta.servlet.http.HttpServletRequest request) {
        String fromMdc = MDC.get("correlationId");
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        String fromHeader = request.getHeader("X-Correlation-Id");
        return (fromHeader != null && !fromHeader.isBlank())
                ? fromHeader
                : UUID.randomUUID().toString();
    }
}
