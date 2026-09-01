package com.neuroassist.conv.application.port.out;

import com.neuroassist.conv.domain.model.ConvNoArvoreFala;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConvNoArvoreFalaRepositoryPort {

    ConvNoArvoreFala save(ConvNoArvoreFala entity);

    Optional<ConvNoArvoreFala> findById(UUID id);

    List<ConvNoArvoreFala> findByTenantId(String tenantId);

    // TODO[SCAFFOLD]: add domain-specific query methods
}
