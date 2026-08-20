package com.neuroassist.hist.infrastructure.persistence.adapter;

import com.neuroassist.hist.application.port.out.StreamHeadRepositoryPort;
import com.neuroassist.hist.domain.model.StreamHead;
import com.neuroassist.hist.infrastructure.persistence.repository.StreamHeadJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** P3 stub — stream-head cache adapter. Implemented in HIST-010. */
@Component
@RequiredArgsConstructor
public class StreamHeadRepositoryAdapter implements StreamHeadRepositoryPort {

    private final StreamHeadJpaRepository repository;

    @Override
    public StreamHead save(StreamHead head, String tenantId) {
        // TODO: P3 — map domain model → JPA entity → persist/merge → map back
        throw new UnsupportedOperationException("not implemented — deferred to P3");
    }

    @Override
    public Optional<StreamHead> findByStreamKeyForUpdate(String streamKey, String tenantId) {
        // TODO: P3 — query with PESSIMISTIC_WRITE lock via JPA @Lock
        throw new UnsupportedOperationException("not implemented — deferred to P3");
    }
}
