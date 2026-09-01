package com.neuroassist.conv.application.port.out;

import com.neuroassist.conv.domain.model.ConvArvoreFala;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConvArvoreFalaRepositoryPort {

    ConvArvoreFala save(ConvArvoreFala entity);

    Optional<ConvArvoreFala> findById(UUID id);

    List<ConvArvoreFala> findByTenantId(String tenantId);

    // TODO[SCAFFOLD]: add domain-specific query methods
}
