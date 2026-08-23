package com.neuroassist.pac.infrastructure.config;

import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Feign interceptors — propagate correlationId, tenantId and JWT bearer token to downstream
 * services (RULE-06: only in infrastructure.clients/config).
 */
@Configuration
public class FeignClientConfig {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String EMPRESA_HEADER = "X-Empresa-Id";
    private static final String AUTH_HEADER = "Authorization";
    private static final String MDC_CORRELATION_KEY = "correlationId";

    /**
     * Precedence of {@link CallerTokenPropagationInterceptor}. Must be strictly higher (numerically
     * lower) than {@code ServiceIdentityInterceptor}'s {@code LOWEST_PRECEDENCE} so the
     * service-identity swap always runs last on hist-service calls.
     */
    public static final int CALLER_TOKEN_PROPAGATION_ORDER = 0;

    @Bean
    public RequestInterceptor feignCorrelationIdInterceptor() {
        return (RequestTemplate template) -> {
            String correlationId = MDC.get(MDC_CORRELATION_KEY);
            if (correlationId != null && !correlationId.isBlank()) {
                template.header(CORRELATION_HEADER, correlationId);
            }
        };
    }

    @Bean
    public RequestInterceptor feignTenantIdInterceptor() {
        return (RequestTemplate template) -> {
            String tenantId = TenantContextHolder.getTenantIdOrNull();
            if (tenantId != null && !tenantId.isBlank()) {
                template.header(TENANT_HEADER, tenantId);
            }
        };
    }

    /**
     * INT-PAC-005 — propagates X-Empresa-Id on every outbound Feign call. In pac-service the tenant
     * context IS the empresa id (schema-per-empresa multi-tenancy). SPEC-10 §3 requires
     * X-Empresa-Id on all outbound calls to ACS, EDGE, GEST, HIST.
     */
    @Bean
    public RequestInterceptor feignEmpresaIdInterceptor() {
        return (RequestTemplate template) -> {
            String empresaId = TenantContextHolder.getTenantIdOrNull();
            if (empresaId != null && !empresaId.isBlank()) {
                template.header(EMPRESA_HEADER, empresaId);
            }
        };
    }

    /**
     * Propagates the caller's bearer token to downstream services — the default for calls whose
     * authorization depends on <i>who asked</i> (ACS directory, GEST eligibility, EDGE device
     * validation).
     *
     * <p>Returns a named, {@code @Order}ed class rather than a lambda on purpose: Spring Cloud
     * OpenFeign sorts interceptors with {@code AnnotationAwareOrderComparator}, which reads the
     * annotation off the instance's class. A lambda would be unordered (== {@code
     * LOWEST_PRECEDENCE}) and would tie with {@code ServiceIdentityInterceptor}, making the final
     * {@code Authorization} header on hist-service calls non-deterministic.
     */
    @Bean
    public RequestInterceptor feignAuthPropagationInterceptor() {
        return new CallerTokenPropagationInterceptor();
    }

    /**
     * @see #feignAuthPropagationInterceptor()
     */
    @Order(CALLER_TOKEN_PROPAGATION_ORDER)
    public static final class CallerTokenPropagationInterceptor implements RequestInterceptor {

        @Override
        public void apply(RequestTemplate template) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                if (jwt != null && jwt.getTokenValue() != null) {
                    template.header(AUTH_HEADER, "Bearer " + jwt.getTokenValue());
                }
            }
        }
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
