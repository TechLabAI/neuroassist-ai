package com.neuroassist.gest.infrastructure.persistence.repository;

import com.neuroassist.gest.infrastructure.persistence.entity.TipoGestoJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TipoGestoJpaRepository extends JpaRepository<TipoGestoJpaEntity, UUID> {

    // Multi-tenancy is via schema routing (search_path), not a tenant_id column.
    // The DataSource search_path is set per-request by MultiTenantConnectionProvider.

    /**
     * Returns {@code true} when a primitive with the same name (case-insensitive) already exists
     * for the current tenant and is NOT in state {@code deprecated}. Implements the duplicate-name
     * invariant from ADR-006 (GEST-011).
     */
    @Query(
            "SELECT CASE WHEN COUNT(t) > 0 THEN TRUE ELSE FALSE END"
                    + " FROM TipoGestoJpaEntity t"
                    + " WHERE LOWER(t.nome) = LOWER(:nome)"
                    + " AND t.detectabilityState <> 'deprecated'")
    boolean existsByNomeLowerCaseAndStateNotDeprecated(@Param("nome") String nome);
}
