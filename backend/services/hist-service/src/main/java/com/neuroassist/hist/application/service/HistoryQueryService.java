package com.neuroassist.hist.application.service;

import com.neuroassist.hist.application.port.in.HistoryQueryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HistoryQueryService implements HistoryQueryUseCase {

    @Override
    public void queryTimeline() {
        // TODO: implement
        throw new UnsupportedOperationException("queryTimeline not implemented");
    }

}
