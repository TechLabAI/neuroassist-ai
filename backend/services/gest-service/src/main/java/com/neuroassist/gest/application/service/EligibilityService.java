package com.neuroassist.gest.application.service;

import com.neuroassist.gest.application.port.in.EligibilityUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EligibilityService implements EligibilityUseCase {

    @Override
    public void check() {
        // TODO: implement
        throw new UnsupportedOperationException("check not implemented");
    }
}
