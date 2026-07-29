package com.neuroassist.edge.application.service;

import com.neuroassist.edge.application.port.in.DeviceRegistrationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DeviceRegistrationService implements DeviceRegistrationUseCase {

    @Override
    public void register() {
        // TODO: implement
        throw new UnsupportedOperationException("register not implemented");
    }

    @Override
    public void enroll() {
        // TODO: implement
        throw new UnsupportedOperationException("enroll not implemented");
    }

    @Override
    public void retire() {
        // TODO: implement
        throw new UnsupportedOperationException("retire not implemented");
    }
}
