package com.neuroassist.edge.application.service;

import com.neuroassist.edge.application.port.in.HeartbeatUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HeartbeatService implements HeartbeatUseCase {

    @Override
    public void recordHeartbeat() {
        // TODO: implement
        throw new UnsupportedOperationException("recordHeartbeat not implemented");
    }
}
