package com.neuroassist.gest.application.service;

import com.neuroassist.gest.application.port.in.DetectionContractUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DetectionContractService implements DetectionContractUseCase {

    @Override
    public void getContractDescriptor() {
        // TODO: implement
        throw new UnsupportedOperationException("getContractDescriptor not implemented");
    }

    @Override
    public void validateReport() {
        // TODO: implement
        throw new UnsupportedOperationException("validateReport not implemented");
    }
}
