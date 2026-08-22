package com.neuroassist.pac.application.port.out;

import com.neuroassist.pac.domain.model.TipoGestoRef;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TipoGestoRefRepositoryPort {

    TipoGestoRef save(TipoGestoRef entity);

    Optional<TipoGestoRef> findById(UUID id);

    List<TipoGestoRef> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
