package com.neuroassist.acs.infrastructure.persistence.repository;

import com.neuroassist.acs.infrastructure.persistence.entity.SigningKeyJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SigningKeyJpaRepository extends JpaRepository<SigningKeyJpaEntity, UUID> {

    List<SigningKeyJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
