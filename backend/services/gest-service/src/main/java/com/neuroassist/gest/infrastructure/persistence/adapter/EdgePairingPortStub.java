package com.neuroassist.gest.infrastructure.persistence.adapter;

import com.neuroassist.gest.application.port.out.EdgePairingPort;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * PoC stub for EdgePairingPort — returns a fixed tenant for all devices. Active only when profile
 * "poc" is active; overrides the real {@link
 * com.neuroassist.gest.infrastructure.clients.EdgePairingAdapter} via {@code @Primary}.
 */
@Primary
@Component
@Profile("poc")
public class EdgePairingPortStub implements EdgePairingPort {

    @Override
    public String checkPairingAndGetTenant(UUID deviceId) {
        return "poc";
    }
}
