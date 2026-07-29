package com.neuroassist.pac.application.service;

import com.neuroassist.pac.application.port.in.ComandoAuthoringUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ComandoAuthoringService implements ComandoAuthoringUseCase {

    @Override
    public void createDraft() {
        // TODO: implement
        throw new UnsupportedOperationException("createDraft not implemented");
    }

    @Override
    public void editDraft() {
        // TODO: implement
        throw new UnsupportedOperationException("editDraft not implemented");
    }
}
