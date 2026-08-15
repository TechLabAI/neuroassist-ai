package com.neuroassist.evnt.infrastructure.security;

import com.neuroassist.evnt.application.port.in.TenantResolverPort;
import org.springframework.stereotype.Component;

/** Adapter: inbound port TenantResolverPort backed by TenantContextHolder (infrastructure). */
@Component
public class TenantResolverAdapter implements TenantResolverPort {

    @Override
    public String getTenantIdRequired() {
        return TenantContextHolder.require();
    }

    @Override
    public String getTenantIdOrNull() {
        return TenantContextHolder.getTenantIdOrNull();
    }
}
