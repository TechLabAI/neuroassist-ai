package com.neuroassist.dash.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration for dash-service.
 * X-Correlation-Id handling is performed at servlet filter level (CorrelationIdFilter),
 * so no HandlerInterceptor registration is needed here.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    // No interceptors: correlation ID is handled by CorrelationIdFilter (OncePerRequestFilter)
    // which runs before Spring Security and covers 401/403 responses as well.
}
