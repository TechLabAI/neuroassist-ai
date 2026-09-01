package com.neuroassist.trei.infrastructure.persistence.repository;

import com.neuroassist.trei.infrastructure.persistence.entity.TreiPropostaLlmJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreiPropostaLlmJpaRepository
        extends JpaRepository<TreiPropostaLlmJpaEntity, UUID> {

    List<TreiPropostaLlmJpaEntity> findByTenantId(String tenantId);

    // TODO[SCAFFOLD]: add domain-specific query methods
}
