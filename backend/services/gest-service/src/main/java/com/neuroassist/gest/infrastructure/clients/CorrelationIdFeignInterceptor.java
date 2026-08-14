package com.neuroassist.gest.infrastructure.clients;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * P5 (GEST-018): Feign interceptor that propagates the {@code X-Correlation-Id} header from the
 * current request's MDC context to all downstream Feign calls.
 *
 * <p>Registered as a Spring-managed {@code @Component}; Spring Cloud OpenFeign automatically
 * discovers all {@link RequestInterceptor} beans in the context (RULE-06: only in
 * infrastructure.clients/config).
 */
@Component
public class CorrelationIdFeignInterceptor implements RequestInterceptor {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    public void apply(RequestTemplate template) {
        String correlationId = MDC.get(MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()) {
            template.header(CORRELATION_HEADER, correlationId);
        }
    }
}
