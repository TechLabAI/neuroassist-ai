package com.neuroassist.acs.infrastructure.persistence.adapter;

import com.neuroassist.acs.application.port.out.HistAuditOutboxPort;
import com.neuroassist.acs.infrastructure.persistence.entity.HistAuditOutboxJpaEntity;
import com.neuroassist.acs.infrastructure.persistence.repository.HistAuditOutboxJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Adapter JPA de {@link HistAuditOutboxPort}. RULE-03: sem {@code @Transactional} aqui. */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistAuditOutboxAdapter implements HistAuditOutboxPort {

    private static final String PENDING = "pending";
    private static final String SENT = "sent";
    private static final String FAILED = "failed";

    /** Truncado para caber numa linha de log e numa tela de triagem; a causa inteira vai no log. */
    private static final int MAX_ERROR_LENGTH = 500;

    private final HistAuditOutboxJpaRepository repository;

    @Override
    public void enqueue(UUID factId, String factType, UUID tenantId, String payloadJson) {
        // Consultado em vez de confiar no índice único: deixar a constraint estourar abortaria a
        // transação do chamador — e o chamador aqui é o login. Ninguém pode ser impedido de
        // autenticar porque a auditoria tentou enfileirar duas vezes.
        if (repository.findByFactId(factId).isPresent()) {
            log.debug("Audit fact {} ja enfileirado, nao enfileira de novo", factId);
            return;
        }
        repository.save(
                HistAuditOutboxJpaEntity.builder()
                        .factId(factId)
                        .factType(factType)
                        .tenantId(tenantId)
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
                                        e.getTenantId(),
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
