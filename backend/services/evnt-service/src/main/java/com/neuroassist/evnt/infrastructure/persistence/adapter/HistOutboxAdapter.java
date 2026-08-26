package com.neuroassist.evnt.infrastructure.persistence.adapter;

import com.neuroassist.evnt.application.port.out.HistOutboxPort;
import com.neuroassist.evnt.infrastructure.persistence.entity.HistOutboxJpaEntity;
import com.neuroassist.evnt.infrastructure.persistence.repository.HistOutboxJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** JPA adapter for {@link HistOutboxPort}. RULE-03: no {@code @Transactional} here. */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistOutboxAdapter implements HistOutboxPort {

    private static final String PENDING = "pending";
    private static final String SENT = "sent";
    private static final String FAILED = "failed";

    /** Truncated to fit a log line and a triage screen; the full cause is in the logs. */
    private static final int MAX_ERROR_LENGTH = 500;

    private final HistOutboxJpaRepository repository;

    @Override
    public void enqueue(UUID factId, String factType, String payloadJson) {
        // Checked rather than relying on the unique index: the direct attempt and this write are
        // two paths for one fact, and letting the constraint fire would abort the caller's
        // transaction — rolling back the very event the fact is about.
        if (repository.findByFactId(factId).isPresent()) {
            log.debug("Audit fact {} already queued, not enqueuing twice", factId);
            return;
        }
        repository.save(
                HistOutboxJpaEntity.builder()
                        .factId(factId)
                        .factType(factType)
                        .payloadJson(payloadJson)
                        .build());
    }

    @Override
    public List<PendingFact> findPending(int limit) {
        return repository
                .findByStatusOrderByCreatedAtAsc(PENDING, PageRequest.of(0, limit))
                .stream()
                .map(
                        e ->
                                new PendingFact(
                                        e.getId(),
                                        e.getFactId(),
                                        e.getFactType(),
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
