package com.neuroassist.acs.application.port.out;

import com.neuroassist.acs.domain.model.Tenant;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port — Tenant repository (neuroassist_global schema). Zero Spring/JPA imports (RULE-01,
 * RULE-02).
 */
public interface TenantRepositoryPort {

    Tenant save(Tenant entity);

    Optional<Tenant> findById(UUID id);
}
