package com.neuroassist.hist.infrastructure.persistence.repository;

import com.neuroassist.hist.infrastructure.persistence.entity.SubjectRefJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SubjectRefJpaRepository extends JpaRepository<SubjectRefJpaEntity, UUID> {

    List<SubjectRefJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
