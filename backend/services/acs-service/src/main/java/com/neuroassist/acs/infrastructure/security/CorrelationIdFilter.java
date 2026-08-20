package com.neuroassist.acs.infrastructure.security;

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
 * MDC filter for correlation ID propagation (ACS-021).
 *
 * <p>Runs at highest priority (before all other filters) to ensure correlationId is available in
 * every log statement and error response.
 *
 * <p>Behaviour:
 *
 * <ul>
 *   <li>Reads X-Correlation-Id from request header; generates a new UUID if absent
 *   <li>Sets MDC keys: correlationId, tenantId (initially "N/A", updated by TenantContextFilter)
 *   <li>Propagates X-Correlation-Id in response header
 *   <li>Clears MDC in finally block (prevents ThreadLocal leaks)
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_TENANT_ID = "tenantId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(HEADER_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_CORRELATION_ID, correlationId);
        MDC.put(MDC_TENANT_ID, "N/A");
        response.addHeader(HEADER_CORRELATION_ID, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_TENANT_ID);
        }
    }
}
