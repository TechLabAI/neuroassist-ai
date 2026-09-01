package com.neuroassist.trei.infrastructure.persistence.adapter;

import com.neuroassist.trei.application.port.out.TreiConteudoAutoriaRepositoryPort;
import com.neuroassist.trei.domain.model.TreiConteudoAutoria;
import com.neuroassist.trei.infrastructure.persistence.repository.TreiConteudoAutoriaJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TreiConteudoAutoriaRepositoryAdapter implements TreiConteudoAutoriaRepositoryPort {

    private final TreiConteudoAutoriaJpaRepository repository;

    @Override
    public TreiConteudoAutoria save(TreiConteudoAutoria entity) {
        // TODO[SCAFFOLD]: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<TreiConteudoAutoria> findById(UUID id) {
        // TODO[SCAFFOLD]: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<TreiConteudoAutoria> findByTenantId(String tenantId) {
        // TODO[SCAFFOLD]: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
