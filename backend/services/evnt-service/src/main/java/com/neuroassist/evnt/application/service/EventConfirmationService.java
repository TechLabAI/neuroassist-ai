package com.neuroassist.evnt.application.service;

import com.neuroassist.evnt.application.port.in.EventConfirmationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EventConfirmationService implements EventConfirmationUseCase {

    @Override
    public void confirm() {
        // TODO: implement
        throw new UnsupportedOperationException("confirm not implemented");
    }

    @Override
    public void acknowledgeDispatch() {
        // TODO: implement
        throw new UnsupportedOperationException("acknowledgeDispatch not implemented");
    }
}
