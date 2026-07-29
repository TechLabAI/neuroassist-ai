package com.neuroassist.gest.application.service;

import com.neuroassist.gest.application.port.in.TipoGestoUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TipoGestoService implements TipoGestoUseCase {

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
    public void register() {
        // TODO: implement
        throw new UnsupportedOperationException("register not implemented");
    }

    @Override
    public void transition() {
        // TODO: implement
        throw new UnsupportedOperationException("transition not implemented");
    }
}
