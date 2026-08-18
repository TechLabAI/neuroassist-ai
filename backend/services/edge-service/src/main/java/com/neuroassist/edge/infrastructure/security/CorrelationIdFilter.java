package com.neuroassist.edge.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet-level filter (EDGE-013, P5) that ensures a {@code X-Correlation-Id} is present on every
 * request and its response.
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} + 1 — before Spring Security's {@code
 * FilterChainProxy} — so the correlation ID is available in all security and application logs,
 * including 401/403 rejection log entries.
 *
 * <p>Lifecycle:
 *
 * <ol>
 *   <li>Read {@code X-Correlation-Id} from the incoming request header.
 *   <li>Generate a random UUID if absent or blank.
 *   <li>Bind to MDC under key {@code correlationId} for structured logging.
 *   <li>Echo the value in the response {@code X-Correlation-Id} header.
 *   <li>Remove from MDC in {@code finally} to prevent ThreadLocal leak across pooled threads.
 * </ol>
 *
 * <p>Replaces {@link CorrelationIdInterceptor} (MVC-level HandlerInterceptor) which was
 * deregistered from {@code WebMvcConfig} in P5. A Servlet filter is required here because rejected
 * requests (401/403) may never reach the DispatcherServlet, meaning an MVC interceptor would never
 * run for those cases.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
