package com.neuroassist.hist.application.service;

import com.neuroassist.hist.application.port.in.FactTypeCatalogUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FactTypeCatalogService implements FactTypeCatalogUseCase {

    @Override
    public void listActive() {
        // TODO: implement
        throw new UnsupportedOperationException("listActive not implemented");
    }

}
