package com.neuroassist.pac.infrastructure.persistence.adapter;

import com.neuroassist.pac.application.port.out.HistOutboxPort;
import com.neuroassist.pac.infrastructure.persistence.repository.HistOutboxJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** JPA adapter for {@link HistOutboxPort}. RULE-03: no {@code @Transactional} here. */
@Component
@RequiredArgsConstructor
public class HistOutboxAdapter implements HistOutboxPort {

    private static final String PENDING = "pending";
    private static final String SENT = "sent";
    private static final String FAILED = "failed";

    private final HistOutboxJpaRepository repository;

    @Override
    public List<PendingFact> findPending(int limit) {
        return repository
                .findByStatusOrderByCreatedAtAsc(PENDING, PageRequest.of(0, limit))
                .stream()
                .map(
                        e ->
                                new PendingFact(
                                        e.getId(),
                                        e.getEventType(),
                                        e.getPayloadJson(),
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
    public void markRetried(UUID id) {
        repository
                .findById(id)
                .ifPresent(
                        e -> {
                            e.setRetryCount(e.getRetryCount() + 1);
                            repository.save(e);
                        });
    }

    @Override
    public void markFailed(UUID id) {
        repository
                .findById(id)
                .ifPresent(
                        e -> {
                            e.setStatus(FAILED);
                            repository.save(e);
                        });
    }
}
