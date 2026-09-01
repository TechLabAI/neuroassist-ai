package com.neuroassist.trei.application.service;

import com.neuroassist.trei.application.port.in.SessaoTreinamentoUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SessaoTreinamentoService implements SessaoTreinamentoUseCase {

    @Override
    public void startSession() {
        throw new UnsupportedOperationException("startSession not implemented");
    }

    @Override
    public void requestProposal() {
        throw new UnsupportedOperationException("requestProposal not implemented");
    }
}
