package com.neuroassist.trei.application.port.out;

import com.neuroassist.trei.domain.model.TreiConteudoAutoria;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreiConteudoAutoriaRepositoryPort {

    TreiConteudoAutoria save(TreiConteudoAutoria entity);

    Optional<TreiConteudoAutoria> findById(UUID id);

    List<TreiConteudoAutoria> findByTenantId(String tenantId);

    // TODO[SCAFFOLD]: add domain-specific query methods
}
