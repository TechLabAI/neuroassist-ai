package com.neuroassist.evnt.infrastructure.persistence.adapter;

import com.neuroassist.evnt.application.port.out.CaregiverRoutingPort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class CaregiverRoutingHardcodedAdapter implements CaregiverRoutingPort {

    @Override
    public String resolveCaregiver(String pacienteId) {
        return "cuidadora-jamilly-fixo";
    }
}
