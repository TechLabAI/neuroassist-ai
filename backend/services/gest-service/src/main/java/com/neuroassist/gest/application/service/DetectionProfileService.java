package com.neuroassist.gest.application.service;

import com.neuroassist.gest.application.port.in.DetectionProfileUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DetectionProfileService implements DetectionProfileUseCase {

    @Override
    public void publish() {
        // TODO: implement
        throw new UnsupportedOperationException("publish not implemented");
    }

    @Override
    public void getActive() {
        // TODO: implement
        throw new UnsupportedOperationException("getActive not implemented");
    }

    @Override
    public void findByVersion() {
        // TODO: implement
        throw new UnsupportedOperationException("findByVersion not implemented");
    }

    @Override
    public void pullByDevice() {
        // TODO: implement
        throw new UnsupportedOperationException("pullByDevice not implemented");
    }
}
