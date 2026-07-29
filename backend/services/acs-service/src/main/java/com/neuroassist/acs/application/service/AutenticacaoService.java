package com.neuroassist.acs.application.service;

import com.neuroassist.acs.application.port.in.AutenticacaoUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AutenticacaoService implements AutenticacaoUseCase {

    @Override
    public void authenticate() {
        // TODO: implement
        throw new UnsupportedOperationException("authenticate not implemented");
    }
}
