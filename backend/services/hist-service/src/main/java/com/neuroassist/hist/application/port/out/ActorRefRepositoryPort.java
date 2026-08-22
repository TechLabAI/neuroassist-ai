package com.neuroassist.hist.application.port.out;

import com.neuroassist.hist.domain.model.ActorRef;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActorRefRepositoryPort {

    ActorRef save(ActorRef entity);

    Optional<ActorRef> findById(UUID id);

    List<ActorRef> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
