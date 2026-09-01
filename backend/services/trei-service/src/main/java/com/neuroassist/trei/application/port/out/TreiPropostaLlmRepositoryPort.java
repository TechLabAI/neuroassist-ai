package com.neuroassist.trei.application.port.out;

import com.neuroassist.trei.domain.model.TreiPropostaLlm;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreiPropostaLlmRepositoryPort {

    TreiPropostaLlm save(TreiPropostaLlm entity);

    Optional<TreiPropostaLlm> findById(UUID id);

    List<TreiPropostaLlm> findByTenantId(String tenantId);

    // TODO[SCAFFOLD]: add domain-specific query methods
}
