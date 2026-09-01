package com.neuroassist.conv.application.service;

import com.neuroassist.conv.application.port.in.BindingResolutionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BindingResolutionService implements BindingResolutionUseCase {

    @Override
    public void resolveBinding() {
        throw new UnsupportedOperationException("resolveBinding not implemented");
    }
}
