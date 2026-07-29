package com.neuroassist.pac.application.service;

import com.neuroassist.pac.application.port.in.PacienteUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PacienteService implements PacienteUseCase {

    @Override
    public void create() {
        // TODO: implement
        throw new UnsupportedOperationException("create not implemented");
    }

    @Override
    public void findById() {
        // TODO: implement
        throw new UnsupportedOperationException("findById not implemented");
    }

    @Override
    public void listAll() {
        // TODO: implement
        throw new UnsupportedOperationException("listAll not implemented");
    }

    @Override
    public void update() {
        // TODO: implement
        throw new UnsupportedOperationException("update not implemented");
    }
}
