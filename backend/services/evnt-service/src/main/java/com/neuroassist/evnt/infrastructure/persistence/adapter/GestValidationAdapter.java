package com.neuroassist.evnt.infrastructure.persistence.adapter;

import com.neuroassist.evnt.application.port.out.GestValidationPort;
import com.neuroassist.evnt.infrastructure.clients.GestValidationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GestValidationAdapter implements GestValidationPort {

    private final GestValidationClient client;

    @Override
    public boolean validate(GestValidationPort.GestValidationRequest request) {
        var primitives =
                request.tipoGestoIds().stream()
                        .map(GestValidationClient.PrimitiveRef::new)
                        .toList();
        var req = new GestValidationClient.ValidationRequest(request.profileVersion(), primitives);
        return client.validate(req).valid();
    }
}
