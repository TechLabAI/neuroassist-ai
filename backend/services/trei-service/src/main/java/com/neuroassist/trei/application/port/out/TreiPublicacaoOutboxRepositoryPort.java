package com.neuroassist.trei.application.port.out;

import com.neuroassist.trei.domain.model.TreiPublicacaoOutbox;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreiPublicacaoOutboxRepositoryPort {

    TreiPublicacaoOutbox save(TreiPublicacaoOutbox entity);

    Optional<TreiPublicacaoOutbox> findById(UUID id);

    List<TreiPublicacaoOutbox> findByTenantId(String tenantId);

    // TODO[SCAFFOLD]: add domain-specific query methods
}
