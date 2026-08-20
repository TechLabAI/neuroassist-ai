package com.neuroassist.acs.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * MVC-layer correlation ID interceptor.
 *
 * <p>Defers to {@link CorrelationIdFilter} when MDC already has "correlationId" (filter ran first).
 * In MVC integration tests that skip the filter chain, this interceptor is the fallback. Ensures
 * X-Correlation-Id response header is set at MVC layer as well (defense in depth).
 */
@Component
public class CorrelationIdInterceptor implements HandlerInterceptor {

    private static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        // Defer to CorrelationIdFilter if it has already populated MDC (filter ran first)
        if (MDC.get(MDC_KEY) != null) {
            // Ensure response header is set even if response already has it (idempotent)
            if (res.getHeader(HEADER) == null) {
                res.setHeader(HEADER, MDC.get(MDC_KEY));
            }
            return true;
        }

        // Fallback: populate MDC if filter did not run (e.g. MVC test context)
        String id = req.getHeader(HEADER);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, id);
        res.setHeader(HEADER, id);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        // Only clear if interceptor was the one that set the value (filter will clear its own)
        // Safe to call regardless — MDC.remove on a null key is a no-op
        MDC.remove(MDC_KEY);
    }
}
