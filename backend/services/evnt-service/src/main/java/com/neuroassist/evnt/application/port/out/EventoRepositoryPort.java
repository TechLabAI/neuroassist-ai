package com.neuroassist.evnt.application.port.out;

import com.neuroassist.evnt.domain.model.Evento;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventoRepositoryPort {

    Evento save(Evento evento);

    Optional<Evento> findById(UUID id);

    List<Evento> findByFeedback(String feedback);

    /** Returns all eventos with seq > lastSeq for Last-Event-ID backfill. */
    List<Evento> findBySeqGreaterThan(long lastSeq);
}
