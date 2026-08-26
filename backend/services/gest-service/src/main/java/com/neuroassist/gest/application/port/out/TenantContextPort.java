package com.neuroassist.gest.application.port.out;

/** Outbound port for reading the current tenant identifier from the request context. */
public interface TenantContextPort {
    String getTenantIdOrNull();
}
