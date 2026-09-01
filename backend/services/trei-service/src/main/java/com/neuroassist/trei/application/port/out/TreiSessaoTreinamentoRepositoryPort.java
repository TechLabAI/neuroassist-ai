package com.neuroassist.trei.application.port.out;

import com.neuroassist.trei.domain.model.TreiSessaoTreinamento;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreiSessaoTreinamentoRepositoryPort {

    TreiSessaoTreinamento save(TreiSessaoTreinamento entity);

    Optional<TreiSessaoTreinamento> findById(UUID id);

    List<TreiSessaoTreinamento> findByTenantId(String tenantId);

    // TODO[SCAFFOLD]: add domain-specific query methods
}
