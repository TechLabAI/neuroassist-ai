package com.neuroassist.gest.application.port.out;

import com.neuroassist.gest.domain.model.TipoGesto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port — TipoGesto persistence. Multi-tenancy is implicit via schema routing set by
 * TenantContextFilter; no tenantId parameter is needed. Implemented by TipoGestoRepositoryAdapter
 * (infrastructure layer, P3).
 */
public interface TipoGestoRepositoryPort {

    TipoGesto save(TipoGesto tipoGesto);

    Optional<TipoGesto> findById(UUID id);

    /**
     * Returns all primitives for the current tenant. Schema routing is transparent — the JPA
     * adapter delegates to {@code findAll()} which is already scoped to the current tenant's schema
     * via the DataSource search_path set by MultiTenantConnectionProvider.
     */
    List<TipoGesto> findAllByTenant();

    /**
     * Returns {@code true} when a primitive with the same name (case-insensitive) already exists
     * for the current tenant and is NOT in DEPRECATED state. Enforces the duplicate-name invariant
     * from ADR-006 (GEST-006).
     */
    boolean existsByNomeIgnoreCaseAndStateNotDeprecated(String nome);
}
