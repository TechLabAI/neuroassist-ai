package com.neuroassist.acs.infrastructure.persistence.repository;

import com.neuroassist.acs.domain.model.CuidadorStatus;
import com.neuroassist.acs.infrastructure.persistence.entity.CuidadorJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuidadorJpaRepository extends JpaRepository<CuidadorJpaEntity, UUID> {

    /**
     * Finds a caregiver by login (case-insensitive) in the current tenant schema. The schema is
     * resolved at runtime by Hibernate multi-tenancy.
     *
     * @param login caregiver login (case-insensitive comparison via lower())
     * @return Optional CuidadorJpaEntity
     */
    Optional<CuidadorJpaEntity> findByLoginIgnoreCase(String login);

    /**
     * Counts caregivers by status in the current tenant schema.
     *
     * @param status the status to count
     * @return count of matching caregivers
     */
    long countByStatus(CuidadorStatus status);

    /**
     * Lists all caregivers for a given tenant ID. Schema resolved at runtime via Hibernate
     * multi-tenancy.
     *
     * @param tenantId tenant UUID
     * @return list of caregivers belonging to the tenant
     */
    List<CuidadorJpaEntity> findByTenantId(UUID tenantId);
}
