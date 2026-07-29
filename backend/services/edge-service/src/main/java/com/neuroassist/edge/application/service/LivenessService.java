package com.neuroassist.edge.application.service;

import com.neuroassist.edge.application.port.in.LivenessUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LivenessService implements LivenessUseCase {

    @Override
    public void evaluate() {
        // TODO: implement
        throw new UnsupportedOperationException("evaluate not implemented");
    }

    @Override
    public void resolveDeviceForPatient() {
        // TODO: implement
        throw new UnsupportedOperationException("resolveDeviceForPatient not implemented");
    }

    @Override
    public void resolveTenant() {
        // TODO: implement
        throw new UnsupportedOperationException("resolveTenant not implemented");
    }
}
