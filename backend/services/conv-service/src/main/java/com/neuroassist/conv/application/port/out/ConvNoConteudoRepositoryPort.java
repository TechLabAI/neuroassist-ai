package com.neuroassist.conv.application.port.out;

import com.neuroassist.conv.domain.model.ConvNoConteudo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConvNoConteudoRepositoryPort {

    ConvNoConteudo save(ConvNoConteudo entity);

    Optional<ConvNoConteudo> findById(UUID id);

    List<ConvNoConteudo> findByTenantId(String tenantId);

    // TODO[SCAFFOLD]: add domain-specific query methods
}
