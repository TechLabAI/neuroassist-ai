package com.neuroassist.gest.infrastructure.config;

import com.neuroassist.gest.infrastructure.security.TenantContextHolder;
import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Feign interceptors — propagate tenantId and JWT bearer token to downstream services (RULE-06:
 * only in infrastructure.clients/config).
 *
 * <p>X-Correlation-Id propagation is handled by {@link
 * com.neuroassist.gest.infrastructure.clients.CorrelationIdFeignInterceptor} (a {@code @Component}
 * in {@code infrastructure.clients}), auto-discovered by Spring Cloud OpenFeign.
 */
@Configuration
public class FeignClientConfig {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String AUTH_HEADER = "Authorization";

    @Bean
    public RequestInterceptor feignTenantIdInterceptor() {
        return (RequestTemplate template) -> {
            String tenantId = TenantContextHolder.getTenantIdOrNull();
            if (tenantId != null && !tenantId.isBlank()) {
                template.header(TENANT_HEADER, tenantId);
            }
        };
    }

    @Bean
    public RequestInterceptor feignAuthPropagationInterceptor() {
        return (RequestTemplate template) -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                if (jwt != null && jwt.getTokenValue() != null) {
                    template.header(AUTH_HEADER, "Bearer " + jwt.getTokenValue());
                }
            }
        };
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
