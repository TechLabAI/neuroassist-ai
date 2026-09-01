package com.neuroassist.trei.infrastructure.persistence.repository;

import com.neuroassist.trei.infrastructure.persistence.entity.TreiPublicacaoOutboxJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreiPublicacaoOutboxJpaRepository
        extends JpaRepository<TreiPublicacaoOutboxJpaEntity, UUID> {

    List<TreiPublicacaoOutboxJpaEntity> findByTenantId(String tenantId);

    // TODO[SCAFFOLD]: add domain-specific query methods
}
