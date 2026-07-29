package com.neuroassist.pac.application.service;

import com.neuroassist.pac.application.port.in.ComandoLifecycleUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ComandoLifecycleService implements ComandoLifecycleUseCase {

    @Override
    public void validate() {
        // TODO: implement
        throw new UnsupportedOperationException("validate not implemented");
    }

    @Override
    public void publish() {
        // TODO: implement
        throw new UnsupportedOperationException("publish not implemented");
    }

    @Override
    public void recalibrate() {
        // TODO: implement
        throw new UnsupportedOperationException("recalibrate not implemented");
    }

    @Override
    public void retire() {
        // TODO: implement
        throw new UnsupportedOperationException("retire not implemented");
    }
}
