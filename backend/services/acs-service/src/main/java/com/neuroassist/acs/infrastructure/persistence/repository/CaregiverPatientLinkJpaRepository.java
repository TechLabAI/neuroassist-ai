package com.neuroassist.acs.infrastructure.persistence.repository;

import com.neuroassist.acs.infrastructure.persistence.entity.CaregiverPatientLinkJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaregiverPatientLinkJpaRepository
        extends JpaRepository<CaregiverPatientLinkJpaEntity, UUID> {

    List<CaregiverPatientLinkJpaEntity> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
