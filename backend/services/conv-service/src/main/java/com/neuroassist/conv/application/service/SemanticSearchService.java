package com.neuroassist.conv.application.service;

import com.neuroassist.conv.application.port.in.SemanticSearchUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SemanticSearchService implements SemanticSearchUseCase {

    @Override
    public void searchAssisted() {
        throw new UnsupportedOperationException("searchAssisted not implemented");
    }
}
