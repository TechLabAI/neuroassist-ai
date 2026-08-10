package com.neuroassist.evnt.infrastructure.persistence.adapter;

import com.neuroassist.evnt.application.port.out.ReportIdempotencyRepositoryPort;
import com.neuroassist.evnt.infrastructure.persistence.entity.ReportIdempotencyJpaEntity;
import com.neuroassist.evnt.infrastructure.persistence.repository.ReportIdempotencyJpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportIdempotencyRepositoryAdapter implements ReportIdempotencyRepositoryPort {

    private final ReportIdempotencyJpaRepository repository;

    @Override
    public boolean exists(String reportId) {
        return repository.existsById(reportId);
    }

    @Override
    public Optional<UUID> findEventoId(String reportId) {
        return repository.findById(reportId).map(ReportIdempotencyJpaEntity::getEventoId);
    }

    @Override
    public void save(String reportId, UUID eventoId) {
        repository.save(
                ReportIdempotencyJpaEntity.builder()
                        .reportId(reportId)
                        .eventoId(eventoId)
                        .processedAt(Instant.now())
                        .build());
    }
}
