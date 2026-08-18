package com.neuroassist.hist.application.service;

import com.neuroassist.hist.application.port.in.ChainVerificationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChainVerificationService implements ChainVerificationUseCase {

    @Override
    public void verifyStream() {
        // TODO: implement
        throw new UnsupportedOperationException("verifyStream not implemented");
    }

}
