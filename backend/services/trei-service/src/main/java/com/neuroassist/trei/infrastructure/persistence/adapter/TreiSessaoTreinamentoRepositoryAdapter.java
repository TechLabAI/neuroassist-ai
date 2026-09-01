package com.neuroassist.trei.infrastructure.persistence.adapter;

import com.neuroassist.trei.application.port.out.TreiSessaoTreinamentoRepositoryPort;
import com.neuroassist.trei.domain.model.TreiSessaoTreinamento;
import com.neuroassist.trei.infrastructure.persistence.repository.TreiSessaoTreinamentoJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TreiSessaoTreinamentoRepositoryAdapter implements TreiSessaoTreinamentoRepositoryPort {

    private final TreiSessaoTreinamentoJpaRepository repository;

    @Override
    public TreiSessaoTreinamento save(TreiSessaoTreinamento entity) {
        // TODO[SCAFFOLD]: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<TreiSessaoTreinamento> findById(UUID id) {
        // TODO[SCAFFOLD]: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<TreiSessaoTreinamento> findByTenantId(String tenantId) {
        // TODO[SCAFFOLD]: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
