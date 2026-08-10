package com.neuroassist.evnt.infrastructure.persistence.adapter;

import com.neuroassist.evnt.application.port.out.AuditTrailPort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class AuditTrailNoOpAdapter implements AuditTrailPort {

    @Override
    public void append(String action, String entityId, String details) {
        // PoC: no-op — audit events não persistidos nesta fase
    }
}
