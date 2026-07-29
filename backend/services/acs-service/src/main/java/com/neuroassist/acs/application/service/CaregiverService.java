package com.neuroassist.acs.application.service;

import com.neuroassist.acs.application.port.in.CaregiverUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CaregiverService implements CaregiverUseCase {

    @Override
    public void create() {
        // TODO: implement
        throw new UnsupportedOperationException("create not implemented");
    }

    @Override
    public void listAll() {
        // TODO: implement
        throw new UnsupportedOperationException("listAll not implemented");
    }

    @Override
    public void findById() {
        // TODO: implement
        throw new UnsupportedOperationException("findById not implemented");
    }

    @Override
    public void deactivate() {
        // TODO: implement
        throw new UnsupportedOperationException("deactivate not implemented");
    }
}
