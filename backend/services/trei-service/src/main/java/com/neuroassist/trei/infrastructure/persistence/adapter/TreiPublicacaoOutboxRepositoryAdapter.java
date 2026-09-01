package com.neuroassist.trei.infrastructure.persistence.adapter;

import com.neuroassist.trei.application.port.out.TreiPublicacaoOutboxRepositoryPort;
import com.neuroassist.trei.domain.model.TreiPublicacaoOutbox;
import com.neuroassist.trei.infrastructure.persistence.repository.TreiPublicacaoOutboxJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TreiPublicacaoOutboxRepositoryAdapter implements TreiPublicacaoOutboxRepositoryPort {

    private final TreiPublicacaoOutboxJpaRepository repository;

    @Override
    public TreiPublicacaoOutbox save(TreiPublicacaoOutbox entity) {
        // TODO[SCAFFOLD]: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<TreiPublicacaoOutbox> findById(UUID id) {
        // TODO[SCAFFOLD]: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<TreiPublicacaoOutbox> findByTenantId(String tenantId) {
        // TODO[SCAFFOLD]: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
