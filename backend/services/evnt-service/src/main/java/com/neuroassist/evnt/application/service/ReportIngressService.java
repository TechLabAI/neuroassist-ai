package com.neuroassist.evnt.application.service;

import com.neuroassist.evnt.application.port.in.ReportIngressUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReportIngressService implements ReportIngressUseCase {

    @Override
    public void ingestReport() {
        // TODO: implement
        throw new UnsupportedOperationException("ingestReport not implemented");
    }
}
