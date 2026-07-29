package com.neuroassist.pac.application.service;

import com.neuroassist.pac.application.port.in.CatalogoAtivoUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogoAtivoService implements CatalogoAtivoUseCase {

    @Override
    public void getCatalogoAtivo() {
        // TODO: implement
        throw new UnsupportedOperationException("getCatalogoAtivo not implemented");
    }
}
