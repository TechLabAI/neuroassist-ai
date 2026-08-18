package com.neuroassist.dash.application.service;

import com.neuroassist.dash.application.port.in.NotificationDeliveryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationDeliveryService implements NotificationDeliveryUseCase {

    @Override
    public void dispatch() {
        // TODO: implement
        throw new UnsupportedOperationException("dispatch not implemented");
    }

    @Override
    public void pollUnacknowledged() {
        // TODO: implement
        throw new UnsupportedOperationException("pollUnacknowledged not implemented");
    }

    @Override
    public void longPoll() {
        // TODO: implement
        throw new UnsupportedOperationException("longPoll not implemented");
    }

    @Override
    public void acknowledge() {
        // TODO: implement
        throw new UnsupportedOperationException("acknowledge not implemented");
    }
}
