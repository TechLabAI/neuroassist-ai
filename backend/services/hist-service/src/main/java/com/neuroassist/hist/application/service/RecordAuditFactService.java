package com.neuroassist.hist.application.service;

import com.neuroassist.hist.application.port.in.RecordAuditFactUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RecordAuditFactService implements RecordAuditFactUseCase {

    @Override
    public void append() {
        // TODO: implement
        throw new UnsupportedOperationException("append not implemented");
    }

}
