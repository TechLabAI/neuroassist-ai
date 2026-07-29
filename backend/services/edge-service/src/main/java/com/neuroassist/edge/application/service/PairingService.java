package com.neuroassist.edge.application.service;

import com.neuroassist.edge.application.port.in.PairingUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PairingService implements PairingUseCase {

    @Override
    public void pair() {
        // TODO: implement
        throw new UnsupportedOperationException("pair not implemented");
    }

    @Override
    public void unpair() {
        // TODO: implement
        throw new UnsupportedOperationException("unpair not implemented");
    }
}
