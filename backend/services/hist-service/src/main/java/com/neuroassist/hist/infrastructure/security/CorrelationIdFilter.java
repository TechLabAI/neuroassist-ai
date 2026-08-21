package com.neuroassist.hist.infrastructure.security;

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
 * Servlet filter — extracts or generates {@code X-Correlation-Id} for every request, puts it in MDC
 * under the key {@code correlationId} for structured JSON logging, and propagates it in the
 * response header.
 *
 * <p>Runs at {@code HIGHEST_PRECEDENCE + 10} so it executes before Spring Security's
 * DelegatingFilterProxy and before {@link TenantContextFilter} ({@code LOWEST_PRECEDENCE - 10}),
 * ensuring all downstream filters and handlers see the correlation ID in MDC (P7, HIST-018).
 *
 * <p>{@code MDC.remove("correlationId")} is always called in the {@code finally} block to prevent
 * context leaks across threads in the servlet container pool.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

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
