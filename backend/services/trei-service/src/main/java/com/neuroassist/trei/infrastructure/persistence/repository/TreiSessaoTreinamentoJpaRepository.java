package com.neuroassist.trei.infrastructure.persistence.repository;

import com.neuroassist.trei.infrastructure.persistence.entity.TreiSessaoTreinamentoJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreiSessaoTreinamentoJpaRepository
        extends JpaRepository<TreiSessaoTreinamentoJpaEntity, UUID> {

    List<TreiSessaoTreinamentoJpaEntity> findByTenantId(String tenantId);

    // TODO[SCAFFOLD]: add domain-specific query methods
}
