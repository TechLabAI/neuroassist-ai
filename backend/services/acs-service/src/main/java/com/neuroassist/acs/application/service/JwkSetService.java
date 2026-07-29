package com.neuroassist.acs.application.service;

import com.neuroassist.acs.application.port.in.JwkSetUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class JwkSetService implements JwkSetUseCase {

    @Override
    public void getPublicKeys() {
        // TODO: implement
        throw new UnsupportedOperationException("getPublicKeys not implemented");
    }
}
