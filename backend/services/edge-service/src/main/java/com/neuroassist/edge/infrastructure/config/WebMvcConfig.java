package com.neuroassist.edge.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration for edge-service.
 *
 * <p>Note (P5): {@code CorrelationIdInterceptor} was removed from the interceptor registry in P5
 * because {@code CorrelationIdFilter} (a Servlet-level {@code OncePerRequestFilter}) supersedes it.
 * The filter runs before Spring Security, ensuring correlation IDs are available even on 401/403
 * rejected requests. The old MVC interceptor was registered here but could not set the correlation
 * ID for security-rejected requests.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {}
