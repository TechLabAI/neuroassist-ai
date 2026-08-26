package com.neuroassist.evnt.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface ReportIdempotencyRepositoryPort {

    boolean exists(String reportId);

    Optional<UUID> findEventoId(String reportId);

    void save(String reportId, UUID eventoId);
}
