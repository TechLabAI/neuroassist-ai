package com.neuroassist.dash.application.service;

import com.neuroassist.dash.application.port.in.PanelStreamUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PanelStreamService implements PanelStreamUseCase {

    @Override
    public void openSession() {
        // TODO: implement
        throw new UnsupportedOperationException("openSession not implemented");
    }

    @Override
    public void heartbeat() {
        // TODO: implement
        throw new UnsupportedOperationException("heartbeat not implemented");
    }

    @Override
    public void closeSession() {
        // TODO: implement
        throw new UnsupportedOperationException("closeSession not implemented");
    }

    @Override
    public void replayCatchup() {
        // TODO: implement
        throw new UnsupportedOperationException("replayCatchup not implemented");
    }
}
