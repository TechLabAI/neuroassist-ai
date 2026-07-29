package com.neuroassist.evnt.application.service;

import com.neuroassist.evnt.application.port.in.EventQueryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EventQueryService implements EventQueryUseCase {

    @Override
    public void findById() {
        // TODO: implement
        throw new UnsupportedOperationException("findById not implemented");
    }

    @Override
    public void listActive() {
        // TODO: implement
        throw new UnsupportedOperationException("listActive not implemented");
    }

    @Override
    public void listAll() {
        // TODO: implement
        throw new UnsupportedOperationException("listAll not implemented");
    }
}
