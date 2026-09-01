package com.neuroassist.trei.infrastructure.persistence.repository;

import com.neuroassist.trei.infrastructure.persistence.entity.TreiConteudoAutoriaJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreiConteudoAutoriaJpaRepository
        extends JpaRepository<TreiConteudoAutoriaJpaEntity, UUID> {

    List<TreiConteudoAutoriaJpaEntity> findByTenantId(String tenantId);

    // TODO[SCAFFOLD]: add domain-specific query methods
}
