package com.neuroassist.hist.infrastructure.persistence.adapter;

import com.neuroassist.hist.application.port.out.FactTypeRepositoryPort;
import com.neuroassist.hist.infrastructure.persistence.repository.FactTypeJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * P1 stub — fact-type vocabulary adapter. The FactType vocabulary is enum-managed in P1..P8; this
 * adapter is the P9 extension point for DB/config-backed catalog (HIST-023).
 */
@Component
@RequiredArgsConstructor
public class FactTypeRepositoryAdapter implements FactTypeRepositoryPort {

    private final FactTypeJpaRepository repository;

    @Override
    public List<String> findActiveByVersion(String version) {
        // TODO: P9 — query DB for configurable fact types
        throw new UnsupportedOperationException(
                "findActiveByVersion not implemented — deferred to P9");
    }

    @Override
    public Optional<String> findByCode(String code) {
        // TODO: P9 — query DB for configurable fact types
        throw new UnsupportedOperationException("findByCode not implemented — deferred to P9");
    }
}
