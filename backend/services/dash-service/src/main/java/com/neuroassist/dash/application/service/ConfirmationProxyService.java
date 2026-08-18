package com.neuroassist.dash.application.service;

import com.neuroassist.dash.application.port.in.ConfirmationProxyUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ConfirmationProxyService implements ConfirmationProxyUseCase {

    @Override
    public void confirmEvent() {
        // TODO: implement
        throw new UnsupportedOperationException("confirmEvent not implemented");
    }
}
