package com.neuroassist.dash.application.service;

import com.neuroassist.dash.application.port.in.PanelCompositionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PanelCompositionService implements PanelCompositionUseCase {

    @Override
    public void composeSummary() {
        // TODO: implement
        throw new UnsupportedOperationException("composeSummary not implemented");
    }

    @Override
    public void composePatientOverview() {
        // TODO: implement
        throw new UnsupportedOperationException("composePatientOverview not implemented");
    }

    @Override
    public void queryHistory() {
        // TODO: implement
        throw new UnsupportedOperationException("queryHistory not implemented");
    }

    @Override
    public void queryDeviceStatus() {
        // TODO: implement
        throw new UnsupportedOperationException("queryDeviceStatus not implemented");
    }
}
