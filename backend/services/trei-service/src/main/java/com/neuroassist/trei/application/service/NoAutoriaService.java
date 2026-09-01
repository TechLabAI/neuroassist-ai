package com.neuroassist.trei.application.service;

import com.neuroassist.trei.application.port.in.NoAutoriaUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NoAutoriaService implements NoAutoriaUseCase {

    @Override
    public void materializeNode() {
        throw new UnsupportedOperationException("materializeNode not implemented");
    }

    @Override
    public void approveCaregiver() {
        throw new UnsupportedOperationException("approveCaregiver not implemented");
    }

    @Override
    public void approvePatient() {
        throw new UnsupportedOperationException("approvePatient not implemented");
    }

    @Override
    public void inspectNode() {
        throw new UnsupportedOperationException("inspectNode not implemented");
    }

    @Override
    public void discardNode() {
        throw new UnsupportedOperationException("discardNode not implemented");
    }
}
