package com.neuroassist.gest.infrastructure.persistence.adapter;

import com.neuroassist.gest.application.port.out.EdgePairingPort;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * PoC stub for EdgePairingPort — returns a fixed tenant for all devices. Active only when profile
 * "poc" is active.
 */
@Primary
@Component
@Profile("poc")
public class EdgePairingPortStub implements EdgePairingPort {

    @Override
    public String getTenantForDevice(String deviceId) {
        return "poc";
    }
}
