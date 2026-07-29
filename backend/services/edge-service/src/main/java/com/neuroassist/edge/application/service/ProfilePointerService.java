package com.neuroassist.edge.application.service;

import com.neuroassist.edge.application.port.in.ProfilePointerUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProfilePointerService implements ProfilePointerUseCase {

    @Override
    public void currentPointerForTenant() {
        // TODO: implement
        throw new UnsupportedOperationException("currentPointerForTenant not implemented");
    }
}
