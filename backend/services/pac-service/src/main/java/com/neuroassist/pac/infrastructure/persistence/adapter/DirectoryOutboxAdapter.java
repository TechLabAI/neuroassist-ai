package com.neuroassist.pac.infrastructure.persistence.adapter;

import com.neuroassist.pac.application.port.out.DirectoryOutboxPort;
import com.neuroassist.pac.infrastructure.persistence.entity.DirectoryOutboxJpaEntity;
import com.neuroassist.pac.infrastructure.persistence.repository.DirectoryOutboxJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** JPA adapter for {@link DirectoryOutboxPort}. RULE-03: no {@code @Transactional} here. */
@Component
@RequiredArgsConstructor
public class DirectoryOutboxAdapter implements DirectoryOutboxPort {

    private static final String PENDING = "pending";
    private static final String SENT = "sent";
    private static final String FAILED = "failed";
    private static final String LINK = "LINK";
    private static final String UNLINK = "UNLINK";

    /** Truncated to fit a log line and a triage screen; the full cause is in the logs. */
    private static final int MAX_ERROR_LENGTH = 500;

    private final DirectoryOutboxJpaRepository repository;

    @Override
    public void enqueueLink(UUID pacienteId, UUID cuidadorId) {
        enqueue(LINK, pacienteId, cuidadorId);
    }

    @Override
    public void enqueueUnlink(UUID pacienteId, UUID cuidadorId) {
        enqueue(UNLINK, pacienteId, cuidadorId);
    }

    private void enqueue(String operation, UUID pacienteId, UUID cuidadorId) {
        repository.save(
                DirectoryOutboxJpaEntity.builder()
                        .operation(operation)
                        .pacienteId(pacienteId)
                        .cuidadorId(cuidadorId)
                        .build());
    }

    @Override
    public List<PendingEntry> findPending(int limit) {
        return repository
                .findByStatusOrderByCreatedAtAsc(PENDING, PageRequest.of(0, limit))
                .stream()
                .map(
                        e ->
                                new PendingEntry(
                                        e.getId(),
                                        e.getOperation(),
                                        e.getPacienteId(),
                                        e.getCuidadorId(),
                                        e.getRetryCount()))
                .toList();
    }

    @Override
    public void markSent(UUID id) {
        repository
                .findById(id)
                .ifPresent(
                        e -> {
                            e.setStatus(SENT);
                            e.setSentAt(Instant.now());
                            repository.save(e);
                        });
    }

    @Override
    public void markRetried(UUID id, String error) {
        repository
                .findById(id)
                .ifPresent(
                        e -> {
                            e.setRetryCount(e.getRetryCount() + 1);
                            e.setLastError(truncate(error));
                            repository.save(e);
                        });
    }

    @Override
    public void markFailed(UUID id, String error) {
        repository
                .findById(id)
                .ifPresent(
                        e -> {
                            e.setStatus(FAILED);
                            e.setLastError(truncate(error));
                            repository.save(e);
                        });
    }

    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
