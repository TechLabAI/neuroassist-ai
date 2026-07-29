package com.neuroassist.evnt.application.service;

import com.neuroassist.evnt.application.port.in.NotificationOrchestrationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationOrchestrationService implements NotificationOrchestrationUseCase {

    @Override
    public void orchestrate() {
        // TODO: implement
        throw new UnsupportedOperationException("orchestrate not implemented");
    }

    @Override
    public void retrySweep() {
        // TODO: implement
        throw new UnsupportedOperationException("retrySweep not implemented");
    }
}
