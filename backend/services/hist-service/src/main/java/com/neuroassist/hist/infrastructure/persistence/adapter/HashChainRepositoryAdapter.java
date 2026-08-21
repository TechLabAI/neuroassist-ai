package com.neuroassist.hist.infrastructure.persistence.adapter;

import com.neuroassist.hist.application.port.out.HashChainRepositoryPort;
import com.neuroassist.hist.domain.model.HashChain;
import com.neuroassist.hist.infrastructure.persistence.repository.HashChainJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** P3 stub — hash-chain record persistence adapter. Implemented in HIST-010. */
@Component
@RequiredArgsConstructor
public class HashChainRepositoryAdapter implements HashChainRepositoryPort {

    private final HashChainJpaRepository repository;

    @Override
    public HashChain save(HashChain hashChain, String tenantId) {
        // TODO: P3 — map domain model → JPA entity → persist → map back
        throw new UnsupportedOperationException("not implemented — deferred to P3");
    }

    @Override
    public List<HashChain> findByStreamKey(String streamKey, String tenantId) {
        // TODO: P3 — query JPA repository by streamKey + tenantId, ordered by sequenceNo
        throw new UnsupportedOperationException("not implemented — deferred to P3");
    }

    @Override
    public Optional<HashChain> findByStreamKeyAndSequenceNo(
            String streamKey, long sequenceNo, String tenantId) {
        // TODO: P3 — query JPA repository by streamKey + sequenceNo + tenantId
        throw new UnsupportedOperationException("not implemented — deferred to P3");
    }
}
