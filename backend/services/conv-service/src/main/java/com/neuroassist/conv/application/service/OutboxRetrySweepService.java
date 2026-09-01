package com.neuroassist.conv.application.service;

import com.neuroassist.conv.application.port.in.OutboxRetrySweepUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OutboxRetrySweepService implements OutboxRetrySweepUseCase {

    @Override
    public void sweepPendingFacts() {
        throw new UnsupportedOperationException("sweepPendingFacts not implemented");
    }
}
