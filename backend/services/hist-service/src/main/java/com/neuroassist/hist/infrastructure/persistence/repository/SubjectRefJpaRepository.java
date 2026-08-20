package com.neuroassist.hist.infrastructure.persistence.repository;

import com.neuroassist.hist.infrastructure.persistence.entity.SubjectRefJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRefJpaRepository extends JpaRepository<SubjectRefJpaEntity, UUID> {

    List<SubjectRefJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
