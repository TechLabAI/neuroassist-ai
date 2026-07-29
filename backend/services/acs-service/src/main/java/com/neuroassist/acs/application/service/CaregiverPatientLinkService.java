package com.neuroassist.acs.application.service;

import com.neuroassist.acs.application.port.in.CaregiverPatientLinkUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CaregiverPatientLinkService implements CaregiverPatientLinkUseCase {

    @Override
    public void link() {
        // TODO: implement
        throw new UnsupportedOperationException("link not implemented");
    }

    @Override
    public void listLinks() {
        // TODO: implement
        throw new UnsupportedOperationException("listLinks not implemented");
    }

    @Override
    public void revokeLink() {
        // TODO: implement
        throw new UnsupportedOperationException("revokeLink not implemented");
    }
}
