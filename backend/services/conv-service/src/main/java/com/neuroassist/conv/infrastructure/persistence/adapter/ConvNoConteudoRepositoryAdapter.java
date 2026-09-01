package com.neuroassist.conv.infrastructure.persistence.adapter;

import com.neuroassist.conv.application.port.out.ConvNoConteudoRepositoryPort;
import com.neuroassist.conv.domain.model.ConvNoConteudo;
import com.neuroassist.conv.infrastructure.persistence.repository.ConvNoConteudoJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConvNoConteudoRepositoryAdapter implements ConvNoConteudoRepositoryPort {

    private final ConvNoConteudoJpaRepository repository;

    @Override
    public ConvNoConteudo save(ConvNoConteudo entity) {
        // TODO[SCAFFOLD]: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<ConvNoConteudo> findById(UUID id) {
        // TODO[SCAFFOLD]: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<ConvNoConteudo> findByTenantId(String tenantId) {
        // TODO[SCAFFOLD]: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
