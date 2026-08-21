package com.neuroassist.hist.application.service;

import com.neuroassist.hist.application.port.in.AuditRecordQueryUseCase;
import com.neuroassist.hist.application.port.out.AuditRecordRepositoryPort;
import com.neuroassist.hist.domain.model.AuditRecord;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service — single-record fetch by HIST id.
 *
 * <p>P2: service wired to {@link AuditRecordRepositoryPort#findById(UUID, String)}. Real JPA
 * implementation provided in P3.
 *
 * <p>{@code @Transactional(readOnly = true)} only here (RULE-03).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditRecordQueryService implements AuditRecordQueryUseCase {

    private final AuditRecordRepositoryPort auditRecordRepositoryPort;

    @Override
    @Transactional(readOnly = true)
    public Optional<AuditRecord> findById(UUID id, String tenantId) {
        // Delegates to port — JPA adapter implemented in P3
        return auditRecordRepositoryPort.findById(id, tenantId);
    }
}
