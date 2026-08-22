package com.neuroassist.evnt.application.port.out;

public interface AuditTrailPort {

    void append(String action, String entityId, String details);
}
