package com.neuroassist.acs.application.service;

import com.neuroassist.acs.application.port.in.DirectoryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DirectoryService implements DirectoryUseCase {

    @Override
    public void getTenantValidity() {
        // TODO: implement
        throw new UnsupportedOperationException("getTenantValidity not implemented");
    }

    @Override
    public void getRoutingTargets() {
        // TODO: implement
        throw new UnsupportedOperationException("getRoutingTargets not implemented");
    }

    @Override
    public void getCaregiverRole() {
        // TODO: implement
        throw new UnsupportedOperationException("getCaregiverRole not implemented");
    }

    @Override
    public void getPatientScope() {
        // TODO: implement
        throw new UnsupportedOperationException("getPatientScope not implemented");
    }
}
