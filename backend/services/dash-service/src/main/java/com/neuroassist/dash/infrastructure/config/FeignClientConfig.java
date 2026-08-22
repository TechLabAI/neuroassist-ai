package com.neuroassist.dash.infrastructure.config;

import com.neuroassist.dash.infrastructure.security.TenantContextHolder;
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
    private static final String AUTH_HEADER = "Authorization";
    private static final String MDC_CORRELATION_KEY = "correlationId";

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
     * Precedence of {@link CallerTokenPropagationInterceptor}. Must be strictly higher (numerically
     * lower) than {@code EvntServiceIdentityInterceptor}'s {@code LOWEST_PRECEDENCE} so the
     * service-identity swap always runs last on evnt-service calls.
     */
    public static final int CALLER_TOKEN_PROPAGATION_ORDER = 0;

    /**
     * Propagates the caller's bearer token — the default, for calls whose authorization depends on
     * <i>who asked</i> (hist-service timeline reads, edge-service liveness).
     *
     * <p>Returns a named, {@code @Order}ed class rather than a lambda on purpose: Spring Cloud
     * OpenFeign sorts interceptors with {@code AnnotationAwareOrderComparator}, which reads the
     * annotation off the instance's class. A lambda would be unordered (== {@code
     * LOWEST_PRECEDENCE}) and would tie with {@code EvntServiceIdentityInterceptor}, making the
     * final {@code Authorization} header on evnt-service calls non-deterministic.
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
