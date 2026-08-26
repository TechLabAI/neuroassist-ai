package com.neuroassist.acs.application.port.out;

import com.neuroassist.acs.domain.model.Cuidador;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port — Cuidador repository. Operates on the empresa schema (t_<tenantId>) resolved via
 * Hibernate multi-tenancy. Zero Spring/JPA imports (RULE-01, RULE-02).
 */
public interface CuidadorRepositoryPort {

    Cuidador save(Cuidador entity);

    Optional<Cuidador> findById(UUID id);

    List<Cuidador> findByTenantId(UUID tenantId);

    /**
     * Finds a caregiver by login in the current tenant schema (case-insensitive). Used by
     * AutenticacaoService for credential validation.
     *
     * @param login the caregiver login (case-insensitive)
     * @return Optional Cuidador if found in the current tenant schema
     */
    Optional<Cuidador> findByLogin(String login);

    /**
     * Batch-loads caregivers by a list of IDs. Used by DirectoryService to avoid N+1 queries when
     * resolving patient caregivers.
     *
     * @param ids caregiver UUIDs to load
     * @return list of found Cuidador (order not guaranteed; missing IDs are silently skipped)
     */
    List<Cuidador> findAllByIds(List<UUID> ids);

    /**
     * Counts active caregivers in the current tenant schema. Used by LinkService to enforce
     * coverage invariant (SAFETY-CRITICAL).
     *
     * @return count of caregivers with status ACTIVE
     */
    long countActive();
}
