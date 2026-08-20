package com.neuroassist.hist.infrastructure.config;

import com.neuroassist.hist.infrastructure.security.TenantContextHolder;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.concurrent.TimeUnit;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    /**
     * Explicit Feign timeout — programmatic fallback to ensure the timeout is applied regardless of
     * which underlying HTTP client (JDK, Apache HC5, OkHttp) is auto-configured. Properties {@code
     * feign.client.config.acs-directory.*} in YAML may not propagate to all client implementations;
     * this bean guarantees the 2 s connect + 2 s read limit (HIST-012, P4).
     */
    @Bean
    public Request.Options requestOptions(
            @Value("${feign.client.config.acs-directory.connectTimeout:2000}") int connectTimeout,
            @Value("${feign.client.config.acs-directory.readTimeout:2000}") int readTimeout) {
        return new Request.Options(
                connectTimeout, TimeUnit.MILLISECONDS, readTimeout, TimeUnit.MILLISECONDS, true);
    }
}
