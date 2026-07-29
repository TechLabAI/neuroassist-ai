package com.neuroassist.acs.application.service;

import com.neuroassist.acs.application.port.in.TenantUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TenantService implements TenantUseCase {

    @Override
    public void provision() {
        // TODO: implement
        throw new UnsupportedOperationException("provision not implemented");
    }

    @Override
    public void findById() {
        // TODO: implement
        throw new UnsupportedOperationException("findById not implemented");
    }

    @Override
    public void retryProvisioning() {
        // TODO: implement
        throw new UnsupportedOperationException("retryProvisioning not implemented");
    }
}
