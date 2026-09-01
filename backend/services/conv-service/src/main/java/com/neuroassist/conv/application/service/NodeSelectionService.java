package com.neuroassist.conv.application.service;

import com.neuroassist.conv.application.port.in.NodeSelectionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NodeSelectionService implements NodeSelectionUseCase {

    @Override
    public void selectNode() {
        throw new UnsupportedOperationException("selectNode not implemented");
    }
}
