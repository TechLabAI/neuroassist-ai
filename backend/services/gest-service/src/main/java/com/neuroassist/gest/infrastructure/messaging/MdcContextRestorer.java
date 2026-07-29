package com.neuroassist.gest.infrastructure.messaging;

import com.neuroassist.gest.infrastructure.security.TenantContextHolder;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.slf4j.MDC;

/**
 * Restores MDC (correlationId) and TenantContextHolder (tenantId) on the consumer thread from
 * message headers. Async listeners MUST call restore() at start and clear() in finally — RULE-07
 * (CLAUDE.md).
 *
 * <p>JMS/Artemis flavour: reads from JMS message properties.
 */
public final class MdcContextRestorer {

    public static final String CORRELATION_HEADER = "correlationId";
    public static final String TENANT_HEADER = "tenantId";
    public static final String MDC_CORRELATION_KEY = "correlationId";

    private MdcContextRestorer() {}

    public static void restore(Message message) throws JMSException {
        String correlationId = message.getStringProperty(CORRELATION_HEADER);
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(MDC_CORRELATION_KEY, correlationId);
        }
        String tenantId = message.getStringProperty(TENANT_HEADER);
        if (tenantId != null && !tenantId.isBlank()) {
            TenantContextHolder.set(tenantId);
        }
    }

    public static void clear() {
        MDC.remove(MDC_CORRELATION_KEY);
        TenantContextHolder.clear();
    }
}
